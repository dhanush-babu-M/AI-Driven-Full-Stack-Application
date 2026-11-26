package com.pathpilot.github;

import com.pathpilot.config.AppProperties;
import com.pathpilot.github.GitHubClient.GitHubEventsFetchResult;
import com.pathpilot.github.GitHubClient.GitHubUserInfo;
import com.pathpilot.github.GitHubClient.ParsedGitHubEvent;
import com.pathpilot.github.dto.GitHubDtos.GitHubConnectRequest;
import com.pathpilot.github.dto.GitHubDtos.GitHubStatusResponse;
import com.pathpilot.github.dto.GitHubDtos.GitHubSyncResponse;
import com.pathpilot.security.TokenEncryptionService;
import com.pathpilot.user.UserProfile;
import com.pathpilot.user.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GitHubServiceTest {

    private UserProfileRepository userProfileRepository;
    private ConnectedAccountRepository connectedAccountRepository;
    private GithubEventRepository githubEventRepository;
    private GitHubClient gitHubClient;
    private TokenEncryptionService tokenEncryptionService;
    private GitHubService gitHubService;

    private final Map<Long, UserProfile> profiles = new ConcurrentHashMap<>();
    private final Map<Long, ConnectedAccount> accountsByUser = new ConcurrentHashMap<>();
    private final Map<String, GithubEvent> eventsByUserAndExt = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        userProfileRepository = mock(UserProfileRepository.class);
        connectedAccountRepository = mock(ConnectedAccountRepository.class);
        githubEventRepository = mock(GithubEventRepository.class);
        gitHubClient = mock(GitHubClient.class);

        AppProperties props = new AppProperties(
                "PathPilot",
                "Track . Learn . Grow",
                new AppProperties.CorsProperties(List.of("http://localhost:5173")),
                new AppProperties.JwtProperties("test-jwt-secret-must-be-at-least-32-chars", 30, "pathpilot_token", false, "Lax"),
                new AppProperties.RateLimitProperties(5, 60),
                new AppProperties.GithubProperties("https://api.github.com", 2000, 3000, "test-aes-encryption-secret-key-32b")
        );
        tokenEncryptionService = new TokenEncryptionService(props);
        gitHubService = new GitHubService(
                userProfileRepository,
                connectedAccountRepository,
                githubEventRepository,
                gitHubClient,
                tokenEncryptionService
        );

        UserProfile profile = new UserProfile();
        profile.setUserId(42L);
        profile.setFullName("Test User");
        profile.setTimeZone("Asia/Kolkata");
        profiles.put(42L, profile);

        when(userProfileRepository.findByUserId(anyLong())).thenAnswer(inv ->
                Optional.ofNullable(profiles.get(inv.getArgument(0, Long.class))));
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> {
            UserProfile p = inv.getArgument(0);
            profiles.put(p.getUserId(), p);
            return p;
        });

        when(connectedAccountRepository.findByUserIdAndProviderIgnoreCase(anyLong(), anyString()))
                .thenAnswer(inv -> Optional.ofNullable(accountsByUser.get(inv.getArgument(0, Long.class))));
        when(connectedAccountRepository.save(any(ConnectedAccount.class))).thenAnswer(inv -> {
            ConnectedAccount a = inv.getArgument(0);
            if (a.getId() == null) a.setId(seq.getAndIncrement());
            accountsByUser.put(a.getUserId(), a);
            return a;
        });

        when(githubEventRepository.findAllByUserIdAndExternalEventIdIn(anyLong(), anyCollection()))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0, Long.class);
                    @SuppressWarnings("unchecked")
                    Set<String> extIds = Set.copyOf(inv.getArgument(1));
                    return eventsByUserAndExt.values().stream()
                            .filter(e -> e.getUserId().equals(uid) && extIds.contains(e.getExternalEventId()))
                            .toList();
                });

        when(githubEventRepository.saveAll(anyCollection())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Iterable<GithubEvent> items = inv.getArgument(0);
            for (GithubEvent e : items) {
                if (e.getId() == null) e.setId(seq.getAndIncrement());
                eventsByUserAndExt.put(e.getUserId() + ":" + e.getExternalEventId(), e);
            }
            return items;
        });

        when(githubEventRepository.findAllByUserIdAndEventDateBetween(anyLong(), any(LocalDate.class), any(LocalDate.class)))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0, Long.class);
                    return eventsByUserAndExt.values().stream()
                            .filter(e -> e.getUserId().equals(uid))
                            .toList();
                });
    }

    @Test
    void syncTwiceWithOverlappingEvents_deduplicatesByUserIdAndExternalEventId() {
        ConnectedAccount account = new ConnectedAccount();
        account.setId(1L);
        account.setUserId(42L);
        account.setProvider("GITHUB");
        account.setUsernameOrHandle("octocat");
        account.setSyncEnabled(true);
        accountsByUser.put(42L, account);

        ParsedGitHubEvent e1 = new ParsedGitHubEvent(
                "evt-1", "PushEvent", "octocat/demo",
                Instant.parse("2026-11-26T20:00:00Z"), 2, "Pushed 2 commits", "https://github.com/octocat/demo"
        );
        ParsedGitHubEvent e2 = new ParsedGitHubEvent(
                "evt-2", "CreateEvent", "octocat/demo",
                Instant.parse("2026-11-25T10:00:00Z"), 0, "Created branch main", "https://github.com/octocat/demo"
        );
        ParsedGitHubEvent e3 = new ParsedGitHubEvent(
                "evt-3", "PushEvent", "octocat/demo",
                Instant.parse("2026-11-26T21:00:00Z"), 1, "Pushed 1 commit", "https://github.com/octocat/demo"
        );

        // First sync returns e1, e2 (plus an accidental duplicate e1 in the batch itself)
        when(gitHubClient.fetchUserEvents("octocat", null, null))
                .thenReturn(new GitHubEventsFetchResult(List.of(e1, e2, e1), "\"etag-1\"", false));

        GitHubSyncResponse first = gitHubService.syncForUser(42L);
        assertEquals(2, first.newlyImportedEvents());
        assertEquals(1, first.skippedDuplicates());
        assertEquals(2, eventsByUserAndExt.size());

        // Second sync returns e1, e2 (already stored) and e3 (new)
        when(gitHubClient.fetchUserEvents("octocat", null, "\"etag-1\""))
                .thenReturn(new GitHubEventsFetchResult(List.of(e1, e2, e3), "\"etag-2\"", false));

        GitHubSyncResponse second = gitHubService.syncForUser(42L);
        assertEquals(1, second.newlyImportedEvents());
        assertEquals(2, second.skippedDuplicates());
        assertEquals(3, eventsByUserAndExt.size());

        // Verify Asia/Kolkata local date conversion: 2026-11-26T20:00:00Z => 2026-11-27T01:30+05:30
        assertEquals(LocalDate.of(2026, 11, 27), eventsByUserAndExt.get("42:evt-1").getEventDate());
    }

    @Test
    void connectWithOptionalToken_encryptsAtRestAndNeverExposesToken() {
        when(gitHubClient.verifyUser("octocat", "ghp_super_secret_token"))
                .thenReturn(new GitHubUserInfo("octocat", "https://github.com/octocat"));
        when(gitHubClient.fetchUserEvents("octocat", "ghp_super_secret_token", null))
                .thenReturn(new GitHubEventsFetchResult(List.of(), "\"etag-1\"", false));

        GitHubStatusResponse status = gitHubService.connect(
                42L,
                new GitHubConnectRequest("octocat", "ghp_super_secret_token", false)
        );

        assertTrue(status.connected());
        assertTrue(status.hasToken());
        assertEquals("octocat", status.username());

        ConnectedAccount stored = accountsByUser.get(42L);
        assertNotNull(stored.getEncryptedToken());
        assertNotEquals("ghp_super_secret_token", stored.getEncryptedToken());
        assertEquals("ghp_super_secret_token", tokenEncryptionService.decrypt(stored.getEncryptedToken()));
    }
}
