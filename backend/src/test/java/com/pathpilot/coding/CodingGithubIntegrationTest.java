package com.pathpilot.coding;

import com.pathpilot.github.ConnectedAccount;
import com.pathpilot.github.ConnectedAccountRepository;
import com.pathpilot.github.GitHubClient;
import com.pathpilot.github.GitHubClient.GitHubEventsFetchResult;
import com.pathpilot.github.GitHubClient.GitHubUserInfo;
import com.pathpilot.github.GitHubClient.ParsedGitHubEvent;
import com.pathpilot.github.GithubEvent;
import com.pathpilot.github.GithubEventRepository;
import com.pathpilot.job.JobApplicationRepository;
import com.pathpilot.planner.DailyLogRepository;
import com.pathpilot.planner.GoalRepository;
import com.pathpilot.planner.LearningRecord;
import com.pathpilot.planner.LearningRecordRepository;
import com.pathpilot.planner.NotificationPreferencesRepository;
import com.pathpilot.planner.RecordHistory;
import com.pathpilot.planner.RecordHistoryRepository;
import com.pathpilot.planner.TaskRepository;
import com.pathpilot.planner.WeeklyReviewRepository;
import com.pathpilot.project.ProjectMilestoneRepository;
import com.pathpilot.project.ProjectRepository;
import com.pathpilot.security.AuthRateLimitFilter;
import com.pathpilot.user.User;
import com.pathpilot.user.UserProfile;
import com.pathpilot.user.UserProfileRepository;
import com.pathpilot.user.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
})
@ActiveProfiles("local")
class CodingGithubIntegrationTest {

    @Autowired private WebApplicationContext context;
    @Autowired private AuthRateLimitFilter authRateLimitFilter;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private UserRepository userRepository;
    @MockitoBean private UserProfileRepository userProfileRepository;
    @MockitoBean private TaskRepository taskRepository;
    @MockitoBean private DailyLogRepository dailyLogRepository;
    @MockitoBean private LearningRecordRepository learningRecordRepository;
    @MockitoBean private RecordHistoryRepository recordHistoryRepository;
    @MockitoBean private GoalRepository goalRepository;
    @MockitoBean private WeeklyReviewRepository weeklyReviewRepository;
    @MockitoBean private NotificationPreferencesRepository notificationPreferencesRepository;
    @MockitoBean private CodingActivityRepository codingActivityRepository;
    @MockitoBean private GithubEventRepository githubEventRepository;
    @MockitoBean private ConnectedAccountRepository connectedAccountRepository;
    @MockitoBean private ProjectRepository projectRepository;
    @MockitoBean private ProjectMilestoneRepository projectMilestoneRepository;
    @MockitoBean private JobApplicationRepository jobApplicationRepository;
    @MockitoBean private GitHubClient gitHubClient;

    private MockMvc mockMvc;
    private final Map<Long, User> users = new ConcurrentHashMap<>();
    private final Map<Long, UserProfile> profiles = new ConcurrentHashMap<>();
    private final Map<Long, CodingActivity> codings = new ConcurrentHashMap<>();
    private final Map<Long, ConnectedAccount> accountsByUser = new ConcurrentHashMap<>();
    private final List<GithubEvent> events = new CopyOnWriteArrayList<>();
    private final List<RecordHistory> histories = new CopyOnWriteArrayList<>();
    private final AtomicLong seq = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        authRateLimitFilter.clearAll();
        users.clear();
        profiles.clear();
        codings.clear();
        accountsByUser.clear();
        events.clear();
        histories.clear();

        when(userRepository.existsByEmailIgnoreCase(anyString())).thenAnswer(inv ->
                users.values().stream().anyMatch(u -> u.getEmail().equalsIgnoreCase(inv.getArgument(0, String.class))));
        when(userRepository.existsByPhone(anyString())).thenAnswer(inv ->
                users.values().stream().anyMatch(u -> u.getPhone().equals(inv.getArgument(0, String.class))));
        when(userRepository.findByEmailIgnoreCase(anyString())).thenAnswer(inv ->
                users.values().stream().filter(u -> u.getEmail().equalsIgnoreCase(inv.getArgument(0, String.class))).findFirst());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) u.setId(seq.getAndIncrement());
            users.put(u.getId(), u);
            return u;
        });
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> {
            UserProfile p = inv.getArgument(0);
            if (p.getId() == null) p.setId(seq.getAndIncrement());
            profiles.put(p.getUserId(), p);
            return p;
        });
        when(userProfileRepository.findByUserId(anyLong())).thenAnswer(inv ->
                Optional.ofNullable(profiles.get(inv.getArgument(0, Long.class))));

        when(codingActivityRepository.save(any(CodingActivity.class))).thenAnswer(inv -> {
            CodingActivity c = inv.getArgument(0);
            if (c.getId() == null) c.setId(seq.getAndIncrement());
            codings.put(c.getId(), c);
            return c;
        });
        when(codingActivityRepository.findByIdAndUserIdAndDeletedAtUtcIsNull(anyLong(), anyLong()))
                .thenAnswer(inv -> {
                    Long id = inv.getArgument(0);
                    Long uid = inv.getArgument(1);
                    CodingActivity c = codings.get(id);
                    return (c != null && c.getUserId().equals(uid) && c.getDeletedAtUtc() == null)
                            ? Optional.of(c) : Optional.empty();
                });
        when(codingActivityRepository.findAllByUserIdAndDeletedAtUtcIsNullAndActivityDateBetweenOrderByActivityDateDescIdDesc(
                anyLong(), any(LocalDate.class), any(LocalDate.class)))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0);
                    LocalDate s = inv.getArgument(1);
                    LocalDate e = inv.getArgument(2);
                    return codings.values().stream()
                            .filter(c -> c.getUserId().equals(uid) && c.getDeletedAtUtc() == null
                                    && !c.getActivityDate().isBefore(s) && !c.getActivityDate().isAfter(e))
                            .toList();
                });
        when(codingActivityRepository.findAllByUserIdAndDeletedAtUtcIsNullOrderByActivityDateDescIdDesc(anyLong()))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0);
                    return codings.values().stream()
                            .filter(c -> c.getUserId().equals(uid) && c.getDeletedAtUtc() == null)
                            .sorted((a, b) -> b.getActivityDate().compareTo(a.getActivityDate()))
                            .toList();
                });
        when(codingActivityRepository.findAllByUserIdAndActivityDateAndDeletedAtUtcIsNullOrderByCreatedAtUtcAscIdAsc(
                anyLong(), any(LocalDate.class)))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0);
                    LocalDate d = inv.getArgument(1);
                    return codings.values().stream()
                            .filter(c -> c.getUserId().equals(uid) && c.getDeletedAtUtc() == null && c.getActivityDate().equals(d))
                            .toList();
                });

        when(connectedAccountRepository.findByUserIdAndProviderIgnoreCase(anyLong(), anyString()))
                .thenAnswer(inv -> Optional.ofNullable(accountsByUser.get(inv.getArgument(0, Long.class))));
        when(connectedAccountRepository.save(any(ConnectedAccount.class))).thenAnswer(inv -> {
            ConnectedAccount a = inv.getArgument(0);
            if (a.getId() == null) a.setId(seq.getAndIncrement());
            accountsByUser.put(a.getUserId(), a);
            return a;
        });

        when(githubEventRepository.findAllByUserIdAndEventDateBetween(anyLong(), any(LocalDate.class), any(LocalDate.class)))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0);
                    return events.stream().filter(e -> e.getUserId().equals(uid)).toList();
                });
        when(githubEventRepository.findAllByUserIdAndEventDateOrderByOccurredAtUtcDescIdDesc(anyLong(), any(LocalDate.class)))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0);
                    LocalDate d = inv.getArgument(1);
                    return events.stream().filter(e -> e.getUserId().equals(uid) && e.getEventDate().equals(d)).toList();
                });
        when(githubEventRepository.findAllByUserIdAndExternalEventIdIn(anyLong(), anyCollection()))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0);
                    @SuppressWarnings("unchecked")
                    Set<String> ids = Set.copyOf(inv.getArgument(1));
                    return events.stream()
                            .filter(e -> e.getUserId().equals(uid) && ids.contains(e.getExternalEventId()))
                            .toList();
                });
        when(githubEventRepository.saveAll(anyCollection())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Iterable<GithubEvent> list = inv.getArgument(0);
            for (GithubEvent e : list) {
                if (e.getId() == null) e.setId(seq.getAndIncrement());
                events.add(e);
            }
            return list;
        });

        when(learningRecordRepository.findAllByUserIdAndRecordDateOrderByCreatedAtUtcAscIdAsc(anyLong(), any(LocalDate.class)))
                .thenReturn(List.<LearningRecord>of());
        when(recordHistoryRepository.save(any(RecordHistory.class))).thenAnswer(inv -> {
            RecordHistory h = inv.getArgument(0);
            histories.add(h);
            return h;
        });
    }

    @Test
    void codingPracticeAndGithubEndpoints_workEndToEndWithAuthAndDeduplication() throws Exception {
        mockMvc.perform(get("/api/coding"))
                .andExpect(status().isUnauthorized());

        Cookie cookie = registerAndLogin("coder@example.com", "9111111111");

        MvcResult created = mockMvc.perform(post("/api/coding")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "activityDate", "2026-11-26",
                                "platform", "LeetCode",
                                "problemTitle", "Valid Parentheses",
                                "topic", "Stack",
                                "difficulty", "Easy",
                                "result", "Solved",
                                "notes", "Matched closing brackets with a stack"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dataSource").value("MANUAL"))
                .andExpect(jsonPath("$.problemTitle").value("Valid Parentheses"))
                .andReturn();

        JsonNode createdNode = objectMapper.readTree(created.getResponse().getContentAsString());
        long codingId = createdNode.get("id").asLong();

        mockMvc.perform(get("/api/coding/streak?from=2026-11-01&to=2026-11-30").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSolved").value(1))
                .andExpect(jsonPath("$.banner.source").value("RULE"));

        when(gitHubClient.verifyUser("octocat", null))
                .thenReturn(new GitHubUserInfo("octocat", "https://github.com/octocat"));
        when(gitHubClient.fetchUserEvents("octocat", null, null))
                .thenReturn(new GitHubEventsFetchResult(List.of(
                        new ParsedGitHubEvent("ext-1", "PushEvent", "octocat/demo",
                                Instant.parse("2026-11-26T10:00:00Z"), 2, "Pushed 2 commits", "https://github.com/octocat/demo")
                ), "\"etag-1\"", false));

        mockMvc.perform(post("/api/github/connect")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "octocat"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.storedEventsCount").value(1));

        // Second sync returns the same event => 0 newly imported, 1 skipped
        when(gitHubClient.fetchUserEvents("octocat", null, "\"etag-1\""))
                .thenReturn(new GitHubEventsFetchResult(List.of(
                        new ParsedGitHubEvent("ext-1", "PushEvent", "octocat/demo",
                                Instant.parse("2026-11-26T10:00:00Z"), 2, "Pushed 2 commits", "https://github.com/octocat/demo")
                ), "\"etag-1\"", false));

        mockMvc.perform(post("/api/github/sync").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newlyImportedEvents").value(0))
                .andExpect(jsonPath("$.skippedDuplicates").value(1));

        mockMvc.perform(put("/api/profile/links")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "leetcodeUrl", "https://leetcode.com/u/octocat",
                                "codingbatUrl", "https://codingbat.com/java"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leetcodeUrl").value("https://leetcode.com/u/octocat"))
                .andExpect(jsonPath("$.codingbatUrl").value("https://codingbat.com/java"));

        mockMvc.perform(get("/api/coding/day/2026-11-26").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codingActivities[0].id").value(codingId))
                .andExpect(jsonPath("$.githubEvents[0].externalEventId").value("ext-1"));
    }

    private Cookie registerAndLogin(String email, String phone) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Coder User",
                                "phone", phone,
                                "email", email,
                                "password", "Password123",
                                "confirmPassword", "Password123"
                        ))))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", email,
                                "password", "Password123"
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        return loginResult.getResponse().getCookie("pathpilot_token");
    }
}
