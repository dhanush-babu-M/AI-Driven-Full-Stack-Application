package com.pathpilot.auth;

import com.pathpilot.ai.AiProposalRepository;
import com.pathpilot.coding.CodingActivityRepository;
import com.pathpilot.github.ConnectedAccountRepository;
import com.pathpilot.github.GithubEventRepository;
import com.pathpilot.job.JobApplicationRepository;
import com.pathpilot.job.JobFollowupRepository;
import com.pathpilot.job.JobStatusHistoryRepository;
import com.pathpilot.planner.DailyLogRepository;
import com.pathpilot.planner.GoalRepository;
import com.pathpilot.planner.LearningRecordRepository;
import com.pathpilot.planner.NotificationPreferencesRepository;
import com.pathpilot.planner.RecordHistoryRepository;
import com.pathpilot.planner.TaskRepository;
import com.pathpilot.planner.WeeklyReviewRepository;
import com.pathpilot.project.ProjectMediaRepository;
import com.pathpilot.project.ProjectMilestoneRepository;
import com.pathpilot.project.ProjectRepository;
import com.pathpilot.project.ProjectWorklogRepository;
import com.pathpilot.report.MonthlyReportRepository;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
class AuthIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AuthRateLimitFilter authRateLimitFilter;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private UserProfileRepository userProfileRepository;

    @MockitoBean
    private TaskRepository taskRepository;

    @MockitoBean
    private DailyLogRepository dailyLogRepository;

    @MockitoBean
    private LearningRecordRepository learningRecordRepository;

    @MockitoBean
    private RecordHistoryRepository recordHistoryRepository;

    @MockitoBean
    private GoalRepository goalRepository;

    @MockitoBean
    private WeeklyReviewRepository weeklyReviewRepository;

    @MockitoBean
    private NotificationPreferencesRepository notificationPreferencesRepository;

    @MockitoBean
    private CodingActivityRepository codingActivityRepository;

    @MockitoBean
    private GithubEventRepository githubEventRepository;

    @MockitoBean
    private ConnectedAccountRepository connectedAccountRepository;

    @MockitoBean
    private ProjectRepository projectRepository;

    @MockitoBean
    private ProjectMilestoneRepository projectMilestoneRepository;

    @MockitoBean
    private ProjectWorklogRepository projectWorklogRepository;

    @MockitoBean
    private ProjectMediaRepository projectMediaRepository;

    @MockitoBean
    private JobApplicationRepository jobApplicationRepository;

    @MockitoBean
    private JobFollowupRepository jobFollowupRepository;

    @MockitoBean
    private JobStatusHistoryRepository jobStatusHistoryRepository;

    @MockitoBean
    private AiProposalRepository aiProposalRepository;

    @MockitoBean
    private MonthlyReportRepository monthlyReportRepository;

    private MockMvc mockMvc;
    private Map<Long, User> usersById;
    private Map<Long, UserProfile> profilesByUserId;
    private AtomicLong idSeq;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        authRateLimitFilter.clearAll();

        usersById = new ConcurrentHashMap<>();
        profilesByUserId = new ConcurrentHashMap<>();
        idSeq = new AtomicLong(1);

        when(userRepository.existsByEmailIgnoreCase(anyString())).thenAnswer(inv -> {
            String email = inv.getArgument(0, String.class);
            return usersById.values().stream().anyMatch(u -> u.getEmail().equalsIgnoreCase(email));
        });

        when(userRepository.existsByPhone(anyString())).thenAnswer(inv -> {
            String phone = inv.getArgument(0, String.class);
            return usersById.values().stream().anyMatch(u -> u.getPhone().equals(phone));
        });

        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User user = inv.getArgument(0);
            if (user.getId() == null) {
                user.setId(idSeq.getAndIncrement());
                user.setCreatedAtUtc(Instant.now());
            }
            user.setUpdatedAtUtc(Instant.now());
            usersById.put(user.getId(), user);
            return user;
        });

        when(userRepository.findById(anyLong())).thenAnswer(inv ->
                Optional.ofNullable(usersById.get(inv.getArgument(0, Long.class))));

        when(userRepository.findByEmailIgnoreCase(anyString())).thenAnswer(inv -> {
            String email = inv.getArgument(0, String.class);
            return usersById.values().stream()
                    .filter(u -> u.getEmail().equalsIgnoreCase(email))
                    .findFirst();
        });

        when(userRepository.findByPhone(anyString())).thenAnswer(inv -> {
            String phone = inv.getArgument(0, String.class);
            return usersById.values().stream()
                    .filter(u -> u.getPhone().equals(phone))
                    .findFirst();
        });

        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> {
            UserProfile profile = inv.getArgument(0);
            if (profile.getId() == null) {
                profile.setId(idSeq.getAndIncrement());
                profile.setCreatedAtUtc(Instant.now());
            }
            profile.setUpdatedAtUtc(Instant.now());
            profilesByUserId.put(profile.getUserId(), profile);
            return profile;
        });

        when(userProfileRepository.findByUserId(anyLong())).thenAnswer(inv ->
                Optional.ofNullable(profilesByUserId.get(inv.getArgument(0, Long.class))));
    }

    @Test
    void meWithoutLogin_returns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void registerLoginAndMeFlow_worksWithHttpOnlyCookie() throws Exception {
        String registerPayload = """
                {
                  "fullName": "Alex Johnson",
                  "phone": "9876543210",
                  "email": "alex@example.com",
                  "password": "Password123",
                  "confirmPassword": "Password123"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("alex@example.com"))
                .andExpect(jsonPath("$.fullName").value("Alex Johnson"))
                .andExpect(jsonPath("$.timeZone").value("Asia/Kolkata"))
                .andExpect(content().string(not(containsString("passwordHash"))));

        String loginPayload = """
                {
                  "identifier": "alex@example.com",
                  "password": "Password123"
                }
                """;

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alex@example.com"))
                .andReturn();

        String setCookieHeader = loginResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookieHeader);
        assertTrue(setCookieHeader.contains("HttpOnly"));
        String tokenValue = extractCookieValue(setCookieHeader, "pathpilot_token");

        mockMvc.perform(get("/api/auth/me")
                        .cookie(new Cookie("pathpilot_token", tokenValue)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Alex Johnson"))
                .andExpect(jsonPath("$.email").value("alex@example.com"));

        mockMvc.perform(get("/api/progress/dashboard")
                        .cookie(new Cookie("pathpilot_token", tokenValue)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.greetingName").value("Alex"))
                .andExpect(jsonPath("$.demo").value(false))
                .andExpect(jsonPath("$.today.plannedCount").value(0))
                .andExpect(jsonPath("$.today.completionRate").doesNotExist())
                .andExpect(jsonPath("$.recommendation.source").value("RULE"));
    }

    @Test
    void loginWithWrongPassword_returnsGeneric401AndSixthAttemptReturns429() throws Exception {
        String registerPayload = """
                {
                  "fullName": "Alex Johnson",
                  "phone": "9876543210",
                  "email": "alex@example.com",
                  "password": "Password123",
                  "confirmPassword": "Password123"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload))
                .andExpect(status().isCreated());

        String badLoginPayload = """
                {
                  "identifier": "alex@example.com",
                  "password": "WrongPassword99"
                }
                """;

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(badLoginPayload))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Invalid credentials"));
        }

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badLoginPayload))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    private String extractCookieValue(String setCookieHeader, String cookieName) {
        String prefix = cookieName + "=";
        int start = setCookieHeader.indexOf(prefix);
        if (start < 0) {
            throw new IllegalStateException("Cookie not found in Set-Cookie header: " + cookieName);
        }
        int valueStart = start + prefix.length();
        int end = setCookieHeader.indexOf(';', valueStart);
        return end > 0 ? setCookieHeader.substring(valueStart, end) : setCookieHeader.substring(valueStart);
    }
}
