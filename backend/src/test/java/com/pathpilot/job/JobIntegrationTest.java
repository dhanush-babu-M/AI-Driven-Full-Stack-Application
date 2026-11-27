package com.pathpilot.job;

import com.pathpilot.coding.CodingActivityRepository;
import com.pathpilot.github.ConnectedAccountRepository;
import com.pathpilot.github.GithubEventRepository;
import com.pathpilot.planner.DailyLogRepository;
import com.pathpilot.planner.Goal;
import com.pathpilot.planner.GoalRepository;
import com.pathpilot.planner.LearningRecordRepository;
import com.pathpilot.planner.NotificationPreferencesRepository;
import com.pathpilot.planner.RecordHistory;
import com.pathpilot.planner.RecordHistoryRepository;
import com.pathpilot.planner.TaskRepository;
import com.pathpilot.planner.WeeklyReviewRepository;
import com.pathpilot.project.ProjectMediaRepository;
import com.pathpilot.project.ProjectMilestoneRepository;
import com.pathpilot.project.ProjectRepository;
import com.pathpilot.project.ProjectWorklogRepository;
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
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class JobIntegrationTest {

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
    @MockitoBean private ProjectWorklogRepository projectWorklogRepository;
    @MockitoBean private ProjectMediaRepository projectMediaRepository;
    @MockitoBean private JobApplicationRepository jobApplicationRepository;
    @MockitoBean private JobFollowupRepository jobFollowupRepository;
    @MockitoBean private JobStatusHistoryRepository jobStatusHistoryRepository;

    private MockMvc mockMvc;
    private final Map<Long, User> users = new ConcurrentHashMap<>();
    private final Map<Long, UserProfile> profiles = new ConcurrentHashMap<>();
    private final Map<Long, JobApplication> jobs = new ConcurrentHashMap<>();
    private final List<JobStatusHistory> histories = new CopyOnWriteArrayList<>();
    private final Map<Long, JobFollowup> followups = new ConcurrentHashMap<>();
    private final Map<Long, Goal> goals = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        authRateLimitFilter.clearAll();
        users.clear();
        profiles.clear();
        jobs.clear();
        histories.clear();
        followups.clear();
        goals.clear();

        when(userRepository.existsByEmailIgnoreCase(anyString())).thenAnswer(inv ->
                users.values().stream().anyMatch(u -> u.getEmail().equalsIgnoreCase(inv.getArgument(0, String.class))));
        when(userRepository.existsByPhone(anyString())).thenAnswer(inv ->
                users.values().stream().anyMatch(u -> u.getPhone().equals(inv.getArgument(0, String.class))));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) u.setId(seq.getAndIncrement());
            u.setCreatedAtUtc(Instant.now());
            u.setUpdatedAtUtc(Instant.now());
            users.put(u.getId(), u);
            return u;
        });
        when(userRepository.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(users.get(inv.getArgument(0, Long.class))));
        when(userRepository.findByEmailIgnoreCase(anyString())).thenAnswer(inv ->
                users.values().stream().filter(u -> u.getEmail().equalsIgnoreCase(inv.getArgument(0, String.class))).findFirst());

        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> {
            UserProfile p = inv.getArgument(0);
            if (p.getId() == null) p.setId(seq.getAndIncrement());
            profiles.put(p.getUserId(), p);
            return p;
        });
        when(userProfileRepository.findByUserId(anyLong())).thenAnswer(inv ->
                Optional.ofNullable(profiles.get(inv.getArgument(0, Long.class))));

        when(jobApplicationRepository.save(any(JobApplication.class))).thenAnswer(inv -> {
            JobApplication j = inv.getArgument(0);
            if (j.getId() == null) j.setId(seq.getAndIncrement());
            j.setCreatedAtUtc(Instant.now());
            j.setUpdatedAtUtc(Instant.now());
            jobs.put(j.getId(), j);
            return j;
        });
        when(jobApplicationRepository.findByIdAndUserIdAndDeletedAtUtcIsNull(anyLong(), anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            Long uid = inv.getArgument(1);
            JobApplication j = jobs.get(id);
            if (j == null || !j.getUserId().equals(uid) || j.getDeletedAtUtc() != null) return Optional.empty();
            return Optional.of(j);
        });
        when(jobApplicationRepository.findAllByUserIdAndDeletedAtUtcIsNullOrderByAppliedDateDescIdDesc(anyLong()))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0);
                    return jobs.values().stream()
                            .filter(j -> j.getUserId().equals(uid) && j.getDeletedAtUtc() == null)
                            .sorted((a, b) -> b.getAppliedDate().compareTo(a.getAppliedDate()))
                            .toList();
                });
        when(jobApplicationRepository.findTop3ByUserIdAndDeletedAtUtcIsNullOrderByAppliedDateDescIdDesc(anyLong()))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0);
                    return jobs.values().stream()
                            .filter(j -> j.getUserId().equals(uid) && j.getDeletedAtUtc() == null)
                            .sorted((a, b) -> b.getAppliedDate().compareTo(a.getAppliedDate()))
                            .limit(3)
                            .toList();
                });
        when(jobApplicationRepository.findAllByUserIdAndDeletedAtUtcIsNullAndAppliedDateBetween(
                anyLong(), any(LocalDate.class), any(LocalDate.class)))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0);
                    LocalDate s = inv.getArgument(1);
                    LocalDate e = inv.getArgument(2);
                    return jobs.values().stream()
                            .filter(j -> j.getUserId().equals(uid) && j.getDeletedAtUtc() == null
                                    && !j.getAppliedDate().isBefore(s) && !j.getAppliedDate().isAfter(e))
                            .toList();
                });

        when(jobStatusHistoryRepository.save(any(JobStatusHistory.class))).thenAnswer(inv -> {
            JobStatusHistory h = inv.getArgument(0);
            if (h.getId() == null) h.setId(seq.getAndIncrement());
            h.setChangedAtUtc(Instant.now());
            histories.add(h);
            return h;
        });
        when(jobStatusHistoryRepository.findAllByUserIdAndJobApplicationIdOrderByChangedAtUtcDescIdDesc(anyLong(), anyLong()))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0);
                    Long jid = inv.getArgument(1);
                    return histories.stream()
                            .filter(h -> h.getUserId().equals(uid) && h.getJobApplicationId().equals(jid))
                            .sorted((a, b) -> b.getId().compareTo(a.getId()))
                            .toList();
                });

        when(jobFollowupRepository.save(any(JobFollowup.class))).thenAnswer(inv -> {
            JobFollowup f = inv.getArgument(0);
            if (f.getId() == null) f.setId(seq.getAndIncrement());
            followups.put(f.getId(), f);
            return f;
        });
        when(jobFollowupRepository.findAllByUserIdAndJobApplicationIdAndDeletedAtUtcIsNullOrderByDueDateAscIdAsc(anyLong(), anyLong()))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0);
                    Long jid = inv.getArgument(1);
                    return followups.values().stream()
                            .filter(f -> f.getUserId().equals(uid) && f.getJobApplicationId().equals(jid) && f.getDeletedAtUtc() == null)
                            .toList();
                });
        when(jobFollowupRepository.findAllByUserIdAndStatusAndDeletedAtUtcIsNullAndDueDateLessThanEqualOrderByDueDateAscIdAsc(
                anyLong(), anyString(), any(LocalDate.class)))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0);
                    String st = inv.getArgument(1);
                    LocalDate maxDue = inv.getArgument(2);
                    return followups.values().stream()
                            .filter(f -> f.getUserId().equals(uid) && f.getDeletedAtUtc() == null
                                    && st.equalsIgnoreCase(f.getStatus()) && !f.getDueDate().isAfter(maxDue))
                            .toList();
                });

        when(goalRepository.save(any(Goal.class))).thenAnswer(inv -> {
            Goal g = inv.getArgument(0);
            if (g.getId() == null) g.setId(seq.getAndIncrement());
            goals.put(g.getId(), g);
            return g;
        });
        when(goalRepository.findAllByUserIdAndPeriodTypeAndStartDateAndDeletedAtUtcIsNullOrderByIdAsc(
                anyLong(), anyString(), any(LocalDate.class)))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0);
                    String scope = inv.getArgument(1);
                    LocalDate start = inv.getArgument(2);
                    return goals.values().stream()
                            .filter(g -> g.getUserId().equals(uid) && g.getDeletedAtUtc() == null
                                    && scope.equals(g.getPeriodType()) && start.equals(g.getStartDate()))
                            .toList();
                });
        when(recordHistoryRepository.save(any(RecordHistory.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void fullJobApplicationFlow_updatesStatsDashboardTimelineRemindersMonthlyGoalAndIsolatesUsers() throws Exception {
        Cookie user1 = registerAndLogin("job1@example.com", "9876500001");
        Cookie user2 = registerAndLogin("job2@example.com", "9876500002");

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        LocalDate tomorrow = today.plusDays(1);
        LocalDate monthStart = YearMonth.from(today).atDay(1);

        // 1. Add 3 applications with different statuses; first has follow-up tomorrow
        MvcResult firstRes = mockMvc.perform(post("/api/jobs")
                        .cookie(user1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "companyName", "Acme Software",
                                "roleTitle", "Junior Java Developer",
                                "status", "APPLIED",
                                "appliedDate", today.toString(),
                                "source", "LINKEDIN",
                                "followupDate", tomorrow.toString()
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statusHistory[0].newStatus").value("APPLIED"))
                .andReturn();

        long firstJobId = objectMapper.readTree(firstRes.getResponse().getContentAsString()).path("id").asLong();

        mockMvc.perform(post("/api/jobs")
                        .cookie(user1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "companyName", "Beacon Systems",
                                "roleTitle", "Backend Engineer",
                                "status", "INTERVIEW",
                                "appliedDate", today.toString(),
                                "source", "COMPANY_SITE"
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/jobs")
                        .cookie(user1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "companyName", "Crest Cloud",
                                "roleTitle", "Full Stack Trainee",
                                "status", "OFFER_RECEIVED",
                                "appliedDate", today.toString(),
                                "source", "REFERRAL"
                        ))))
                .andExpect(status().isCreated());

        // 2. Stats & Dashboard update accurately
        mockMvc.perform(get("/api/jobs/stats").cookie(user1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.applied").value(1))
                .andExpect(jsonPath("$.interviews").value(1))
                .andExpect(jsonPath("$.offers").value(1))
                .andExpect(jsonPath("$.followupsDueThisWeek").value(1));

        mockMvc.perform(get("/api/progress/dashboard").cookie(user1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recentApplications.length()").value(3))
                .andExpect(jsonPath("$.upcomingFollowups.length()").value(1))
                .andExpect(jsonPath("$.upcomingFollowups[0].companyName").value("Acme Software"));

        // 3. Status timeline shows changes with timestamps
        mockMvc.perform(patch("/api/jobs/" + firstJobId + "/status")
                        .cookie(user1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "ASSESSMENT",
                                "note", "HackerRank link received"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusHistory.length()").value(2))
                .andExpect(jsonPath("$.statusHistory[0].previousStatus").value("APPLIED"))
                .andExpect(jsonPath("$.statusHistory[0].newStatus").value("ASSESSMENT"))
                .andExpect(jsonPath("$.statusHistory[0].changedAtUtc").exists());

        // 4. Tomorrow follow-up appears in Upcoming Follow-ups and creates a reminder
        mockMvc.perform(get("/api/jobs/followups/upcoming").cookie(user1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].companyName").value("Acme Software"));

        mockMvc.perform(get("/api/reminders/upcoming?hours=24").cookie(user1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].itemType").value("FOLLOWUP"))
                .andExpect(jsonPath("$[0].jobApplicationId").value(firstJobId));

        // 5. Monthly JOB_APPLICATIONS goal count equals real applications with appliedDate in that month (3)
        mockMvc.perform(post("/api/goals")
                        .cookie(user1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Monthly job applications",
                                "category", "JOB_APPLICATIONS",
                                "scope", "MONTH",
                                "periodStart", monthStart.toString(),
                                "targetCount", 10,
                                "doneCount", 0,
                                "status", "PENDING"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.doneCount").value(3))
                .andExpect(jsonPath("$.doneCountSource").value("CALCULATED"));

        // 6. User 2 cannot access User 1's application
        mockMvc.perform(get("/api/jobs/" + firstJobId).cookie(user2))
                .andExpect(status().isNotFound());
    }

    private Cookie registerAndLogin(String email, String phone) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Job Seeker",
                                "phone", phone,
                                "email", email,
                                "password", "Password123",
                                "confirmPassword", "Password123"
                        ))))
                .andExpect(status().isCreated());

        MvcResult loginRes = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", email,
                                "password", "Password123"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        return loginRes.getResponse().getCookie("pathpilot_token");
    }
}
