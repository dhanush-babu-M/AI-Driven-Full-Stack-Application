package com.pathpilot.github;

import com.pathpilot.common.ApiException;
import com.pathpilot.config.AppProperties;
import com.pathpilot.github.GitHubClient.GitHubEventsFetchResult;
import com.pathpilot.github.dto.GitHubDtos.GitHubRepoDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GitHubClientTest {

    private HttpClient httpClient;
    private GitHubClient gitHubClient;

    @BeforeEach
    void setUp() {
        httpClient = mock(HttpClient.class);
        AppProperties props = new AppProperties(
                "PathPilot",
                "Track . Learn . Grow",
                new AppProperties.CorsProperties(List.of("http://localhost:5173")),
                new AppProperties.JwtProperties("test-jwt-secret-must-be-at-least-32-chars", 30, "pathpilot_token", false, "Lax"),
                new AppProperties.RateLimitProperties(5, 60),
                new AppProperties.GithubProperties("https://api.github.com", 2000, 3000, "test-enc-key"),
                null
        );
        gitHubClient = new GitHubClient(props, new ObjectMapper(), httpClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void fetchUserEvents_parsesPushEventSizeAndReturnsEtag() throws Exception {
        String json = """
                [
                  {
                    "id": "evt-101",
                    "type": "PushEvent",
                    "repo": { "name": "octocat/hello-world" },
                    "created_at": "2026-11-26T10:15:30Z",
                    "payload": {
                      "ref": "refs/heads/main",
                      "size": 3,
                      "distinct_size": 2
                    }
                  },
                  {
                    "id": "evt-102",
                    "type": "PullRequestEvent",
                    "repo": { "name": "octocat/hello-world" },
                    "created_at": "2026-11-25T18:00:00Z",
                    "payload": {
                      "action": "opened",
                      "pull_request": {
                        "title": "Add coding tracker",
                        "html_url": "https://github.com/octocat/hello-world/pull/12"
                      }
                    }
                  }
                ]
                """;
        HttpResponse<String> response = mockResponse(200, json, Map.of("ETag", List.of("\"etag-abc\"")));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        GitHubEventsFetchResult result = gitHubClient.fetchUserEvents("octocat", null, null);
        assertEquals(2, result.events().size());
        assertEquals("\"etag-abc\"", result.etag());
        assertEquals(3, result.events().get(0).commitCount());
        assertEquals("PushEvent", result.events().get(0).eventType());
        assertEquals("https://github.com/octocat/hello-world/pull/12", result.events().get(1).htmlUrl());
    }

    @Test
    @SuppressWarnings("unchecked")
    void fetchUserEvents_returnsNotModifiedOn304() throws Exception {
        HttpResponse<String> response = mockResponse(304, "", Map.of());
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        GitHubEventsFetchResult result = gitHubClient.fetchUserEvents("octocat", null, "\"etag-abc\"");
        assertTrue(result.notModified());
        assertTrue(result.events().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void fetchUserEvents_handles403RateLimitGracefully() throws Exception {
        HttpResponse<String> response = mockResponse(
                403,
                "{\"message\":\"API rate limit exceeded\"}",
                Map.of(
                        "X-RateLimit-Remaining", List.of("0"),
                        "X-RateLimit-Reset", List.of("1795700000")
                )
        );
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        ApiException ex = assertThrows(ApiException.class,
                () -> gitHubClient.fetchUserEvents("octocat", null, null));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatus());
        assertEquals("GITHUB_RATE_LIMITED", ex.getCode());
        assertTrue(ex.getMessage().contains("rate limit"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void fetchUserRepos_handlesTimeoutErrorGracefully() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new HttpTimeoutException("timed out"));

        ApiException ex = assertThrows(ApiException.class, () -> gitHubClient.fetchUserRepos("octocat", null));
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, ex.getStatus());
        assertEquals("GITHUB_TIMEOUT", ex.getCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void fetchUserRepos_parsesLanguageAndPushedAt() throws Exception {
        String json = """
                [
                  {
                    "name": "pathpilot",
                    "full_name": "octocat/pathpilot",
                    "language": "Java",
                    "pushed_at": "2026-11-26T09:00:00Z",
                    "html_url": "https://github.com/octocat/pathpilot",
                    "description": "Career tracker",
                    "stargazers_count": 7,
                    "fork": false,
                    "private": false
                  }
                ]
                """;
        HttpResponse<String> response = mockResponse(200, json, Map.of());
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        List<GitHubRepoDto> repos = gitHubClient.fetchUserRepos("octocat", null);
        assertEquals(1, repos.size());
        assertEquals("Java", repos.get(0).language());
        assertEquals("pathpilot", repos.get(0).name());
        assertEquals(7, repos.get(0).stars());
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> mockResponse(int status, String body, Map<String, List<String>> headers) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(response.headers()).thenReturn(HttpHeaders.of(headers, (k, v) -> true));
        return response;
    }
}
