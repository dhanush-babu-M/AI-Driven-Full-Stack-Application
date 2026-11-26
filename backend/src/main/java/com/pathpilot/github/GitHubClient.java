package com.pathpilot.github;

import com.pathpilot.common.ApiException;
import com.pathpilot.config.AppProperties;
import com.pathpilot.github.dto.GitHubDtos.GitHubRepoDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Server-side GitHub REST API v2022-11-28 client with timeouts, ETag conditional requests,
 * and friendly primary/secondary rate-limit handling.
 */
@Component
public class GitHubClient {

    private static final String API_VERSION = "2022-11-28";
    private static final String ACCEPT_HEADER = "application/vnd.github+json";
    private static final String USER_AGENT = "PathPilot-App";
    private static final int MAX_EVENT_PAGES = 3; // GitHub Events API caps timelines at 300 events (3 x 100)
    private static final int EVENTS_PER_PAGE = 100;
    private static final DateTimeFormatter RESET_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public GitHubClient(AppProperties appProperties, ObjectMapper objectMapper) {
        this(
                appProperties,
                objectMapper,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(appProperties.github().connectTimeoutMs()))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build()
        );
    }

    GitHubClient(AppProperties appProperties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public GitHubUserInfo verifyUser(String username, String optionalToken) {
        String encoded = encodePathSegment(username);
        HttpResponse<String> response = sendGet("/users/" + encoded, optionalToken, null);
        if (response.statusCode() == 404) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "GITHUB_USER_NOT_FOUND",
                    "GitHub user '" + username + "' was not found."
            );
        }
        ensureSuccess(response, username);
        JsonNode root = parseJson(response.body());
        return new GitHubUserInfo(
                root.path("login").asText(username),
                root.path("html_url").asText("https://github.com/" + username)
        );
    }

    public GitHubEventsFetchResult fetchUserEvents(String username, String optionalToken, String previousEtag) {
        String encoded = encodePathSegment(username);
        // Per docs.github.com/en/rest/activity/events:
        // /users/{username}/events returns public + private events only when authenticated as that user;
        // /users/{username}/events/public returns public events without requiring auth.
        String pathBase = (optionalToken != null && !optionalToken.isBlank())
                ? "/users/" + encoded + "/events"
                : "/users/" + encoded + "/events/public";

        List<ParsedGitHubEvent> allEvents = new ArrayList<>();
        String newestEtag = previousEtag;

        for (int page = 1; page <= MAX_EVENT_PAGES; page++) {
            String path = pathBase + "?per_page=" + EVENTS_PER_PAGE + "&page=" + page;
            String etagHeader = (page == 1 && previousEtag != null && !previousEtag.isBlank()) ? previousEtag : null;
            HttpResponse<String> response = sendGet(path, optionalToken, etagHeader);

            if (page == 1 && response.statusCode() == 304) {
                return new GitHubEventsFetchResult(List.of(), previousEtag, true);
            }
            if (response.statusCode() == 404) {
                throw new ApiException(
                        HttpStatus.NOT_FOUND,
                        "GITHUB_USER_NOT_FOUND",
                        "GitHub user '" + username + "' was not found."
                );
            }
            ensureSuccess(response, username);

            if (page == 1) {
                newestEtag = response.headers().firstValue("ETag").orElse(null);
            }

            JsonNode array = parseJson(response.body());
            if (!array.isArray() || array.isEmpty()) {
                break;
            }
            for (JsonNode node : array) {
                ParsedGitHubEvent parsed = toParsedEvent(node);
                if (parsed != null) {
                    allEvents.add(parsed);
                }
            }
            if (array.size() < EVENTS_PER_PAGE) {
                break;
            }
        }

        return new GitHubEventsFetchResult(allEvents, newestEtag, false);
    }

    public List<GitHubRepoDto> fetchUserRepos(String username, String optionalToken) {
        String path;
        if (optionalToken != null && !optionalToken.isBlank()) {
            path = "/user/repos?sort=pushed&direction=desc&per_page=10&affiliation=owner,collaborator";
        } else {
            String encoded = encodePathSegment(username);
            path = "/users/" + encoded + "/repos?type=owner&sort=pushed&direction=desc&per_page=10";
        }

        HttpResponse<String> response = sendGet(path, optionalToken, null);
        if (response.statusCode() == 404) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "GITHUB_USER_NOT_FOUND",
                    "GitHub user '" + username + "' was not found."
            );
        }
        ensureSuccess(response, username);

        JsonNode array = parseJson(response.body());
        if (!array.isArray()) {
            return List.of();
        }

        List<GitHubRepoDto> repos = new ArrayList<>();
        for (JsonNode node : array) {
            String pushedRaw = textOrNull(node, "pushed_at");
            Instant pushedAt = pushedRaw != null ? parseInstantSafe(pushedRaw) : null;
            repos.add(new GitHubRepoDto(
                    node.path("name").asText(""),
                    node.path("full_name").asText(""),
                    textOrNull(node, "language"),
                    pushedAt,
                    node.path("html_url").asText(""),
                    textOrNull(node, "description"),
                    node.path("stargazers_count").asInt(0),
                    node.path("fork").asBoolean(false),
                    node.path("private").asBoolean(false)
            ));
        }
        return repos;
    }

    private HttpResponse<String> sendGet(String path, String optionalToken, String ifNoneMatchEtag) {
        String base = appProperties.github().apiBaseUrl().replaceAll("/+$", "");
        URI uri = URI.create(base + path);

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(Duration.ofMillis(appProperties.github().readTimeoutMs()))
                .header("Accept", ACCEPT_HEADER)
                .header("X-GitHub-Api-Version", API_VERSION)
                .header("User-Agent", USER_AGENT);

        if (optionalToken != null && !optionalToken.isBlank()) {
            builder.header("Authorization", "Bearer " + optionalToken.trim());
        }
        if (ifNoneMatchEtag != null && !ifNoneMatchEtag.isBlank()) {
            builder.header("If-None-Match", ifNoneMatchEtag);
        }

        try {
            return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException ex) {
            throw new ApiException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "GITHUB_TIMEOUT",
                    "GitHub API request timed out. Please try again shortly."
            );
        } catch (IOException ex) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "GITHUB_UNREACHABLE",
                    "Unable to reach GitHub API right now. Please try again shortly."
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "GITHUB_INTERRUPTED",
                    "GitHub API request was interrupted."
            );
        }
    }

    private void ensureSuccess(HttpResponse<String> response, String username) {
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return;
        }
        if (status == 401) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "GITHUB_TOKEN_INVALID",
                    "GitHub rejected the saved access token. Update or clear your token."
            );
        }
        if (isRateLimited(status, response)) {
            String message = buildRateLimitMessage(response);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "GITHUB_RATE_LIMITED", message);
        }
        if (status == 403) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "GITHUB_FORBIDDEN",
                    "GitHub denied access for '" + username + "'."
            );
        }
        throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "GITHUB_API_ERROR",
                "GitHub API returned status " + status + ". Please try again later."
        );
    }

    private boolean isRateLimited(int status, HttpResponse<String> response) {
        if (status == 429) {
            return true;
        }
        if (status != 403) {
            return false;
        }
        String remaining = response.headers().firstValue("X-RateLimit-Remaining").orElse("");
        if ("0".equals(remaining.trim())) {
            return true;
        }
        String body = response.body() == null ? "" : response.body().toLowerCase(Locale.ROOT);
        return body.contains("rate limit");
    }

    private String buildRateLimitMessage(HttpResponse<String> response) {
        String retryAfter = response.headers().firstValue("Retry-After").orElse(null);
        if (retryAfter != null && !retryAfter.isBlank()) {
            return "GitHub API rate limit reached. Please try again in " + retryAfter.trim() + " seconds.";
        }
        String resetHeader = response.headers().firstValue("X-RateLimit-Reset").orElse(null);
        if (resetHeader != null && !resetHeader.isBlank()) {
            try {
                long epochSeconds = Long.parseLong(resetHeader.trim());
                String resetTime = RESET_FORMATTER.format(Instant.ofEpochSecond(epochSeconds));
                return "GitHub API rate limit reached (60/hr unauthenticated). Resets around " + resetTime
                        + ". You can also add a personal access token later for 5,000/hr.";
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return "GitHub API rate limit reached. Please wait a few minutes and try again.";
    }

    private ParsedGitHubEvent toParsedEvent(JsonNode node) {
        String id = textOrNull(node, "id");
        String type = textOrNull(node, "type");
        String repoName = node.path("repo").path("name").asText("");
        String createdAtRaw = textOrNull(node, "created_at");
        if (id == null || type == null || repoName.isBlank() || createdAtRaw == null) {
            return null;
        }
        Instant occurredAt = parseInstantSafe(createdAtRaw);
        if (occurredAt == null) {
            return null;
        }

        JsonNode payload = node.path("payload");
        int commitCount = 0;
        String summary = type;
        String htmlUrl = "https://github.com/" + repoName;

        if ("PushEvent".equals(type)) {
            int size = payload.path("size").asInt(0);
            int distinctSize = payload.path("distinct_size").asInt(0);
            int commitsLen = payload.path("commits").isArray() ? payload.path("commits").size() : 0;
            commitCount = size > 0 ? size : (distinctSize > 0 ? distinctSize : (commitsLen > 0 ? commitsLen : 1));
            String ref = payload.path("ref").asText("").replace("refs/heads/", "");
            summary = "Pushed " + commitCount + " commit(s)" + (ref.isBlank() ? "" : " to " + ref);
        } else if ("PullRequestEvent".equals(type)) {
            String action = payload.path("action").asText("updated");
            String prTitle = payload.path("pull_request").path("title").asText("");
            String prUrl = payload.path("pull_request").path("html_url").asText("");
            if (!prUrl.isBlank()) {
                htmlUrl = prUrl;
            }
            summary = "Pull request " + action + (prTitle.isBlank() ? "" : ": " + prTitle);
        } else if ("IssuesEvent".equals(type)) {
            String action = payload.path("action").asText("updated");
            String issueTitle = payload.path("issue").path("title").asText("");
            String issueUrl = payload.path("issue").path("html_url").asText("");
            if (!issueUrl.isBlank()) {
                htmlUrl = issueUrl;
            }
            summary = "Issue " + action + (issueTitle.isBlank() ? "" : ": " + issueTitle);
        } else if ("CreateEvent".equals(type)) {
            String refType = payload.path("ref_type").asText("resource");
            String ref = payload.path("ref").asText("");
            summary = "Created " + refType + (ref.isBlank() ? "" : " " + ref);
        } else if ("ForkEvent".equals(type)) {
            summary = "Forked repository";
        } else if ("WatchEvent".equals(type)) {
            summary = "Starred repository";
        }

        return new ParsedGitHubEvent(id, type, repoName, occurredAt, commitCount, summary, htmlUrl);
    }

    private JsonNode parseJson(String body) {
        try {
            return objectMapper.readTree(body == null || body.isBlank() ? "{}" : body);
        } catch (Exception ex) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "GITHUB_PARSE_ERROR",
                    "Received an unexpected response from GitHub."
            );
        }
    }

    private Instant parseInstantSafe(String raw) {
        try {
            return Instant.parse(raw);
        } catch (Exception ex) {
            return null;
        }
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode val = node.path(field);
        if (val.isMissingNode() || val.isNull()) {
            return null;
        }
        String text = val.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private String encodePathSegment(String value) {
        return URLEncoder.encode(value.trim(), StandardCharsets.UTF_8).replace("+", "%20");
    }

    public record GitHubUserInfo(String login, String htmlUrl) {
    }

    public record ParsedGitHubEvent(
            String externalEventId,
            String eventType,
            String repoName,
            Instant occurredAtUtc,
            int commitCount,
            String payloadSummary,
            String htmlUrl
    ) {
    }

    public record GitHubEventsFetchResult(
            List<ParsedGitHubEvent> events,
            String etag,
            boolean notModified
    ) {
    }
}
