package com.pathpilot.ai;

import com.pathpilot.ai.LlmClient.LlmResult;
import com.pathpilot.coding.CodingActivityRepository;
import com.pathpilot.github.ConnectedAccountRepository;
import com.pathpilot.github.GithubEventRepository;
import com.pathpilot.job.JobApplication;
import com.pathpilot.job.JobApplicationRepository;
import com.pathpilot.job.JobFollowupRepository;
import com.pathpilot.job.JobStatusHistoryRepository;
import com.pathpilot.planner.DailyLogRepository;
import com.pathpilot.planner.GoalRepository;
import com.pathpilot.planner.LearningRecordRepository;
import com.pathpilot.planner.NotificationPreferencesRepository;
import com.pathpilot.planner.RecordHistory;
import com.pathpilot.planner.RecordHistoryRepository;
import com.pathpilot.planner.Task;
import com.pathpilot.planner.TaskRepository;
import com.pathpilot.planner.WeeklyReviewRepository;
import com.pathpilot.project.Project;
import com.pathpilot.project.ProjectMediaRepository;
import com.pathpilot.project.ProjectMilestoneRepository;
import com.pathpilot.project.ProjectRepository;
import com.pathpilot.project.ProjectWorklogRepository;
import com.pathpilot.report.MonthlyReport;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class AiIntegrationTest {

    @Autowired private WebApplicationContext context;
    @Autowired private AuthRateLimitFilter authRateLimitFilter;
    @Autowired private AiRateLimitFilter aiRateLimitFilter;
    @Autowired private AiCacheService aiCacheService;
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
    @MockitoBean private AiProposalRepository aiProposalRepository;
    @MockitoBean private MonthlyReportRepository monthlyReportRepository;
    @MockitoBean private LlmClient llmClient;

    private MockMvc mockMvc;
    private final Map<Long, User> users = new ConcurrentHashMap<>();
    private final Map<Long, UserProfile> profiles = new ConcurrentHashMap<>();
    private final Map<Long, Task> tasks = new ConcurrentHashMap<>();
    private final Map<Long, Project> projects = new ConcurrentHashMap<>();
    private final Map<Long, JobApplication> jobs = new ConcurrentHashMap<>();
    private final Map<Long, AiProposal> proposals = new ConcurrentHashMap<>();
    private final List<RecordHistory> histories = new CopyOnWriteArrayList<>();
    private final AtomicLong seq = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        authRateLimitFilter.clearAll();
        aiRateLimitFilter.clearAll();
        aiCacheService.clearAll();

        users.clear();
        profiles.clear();
        tasks.clear();
        projects.clear();
        jobs.clear();
        proposals.clear();
        histories.clear();

        when(userRepository.existsByEmailIgnoreCase(anyString())).thenAnswer(inv ->
                users.values().stream().anyMatch(u -> u.getEmail().equalsIgnoreCase(inv.getArgument(0, String.class))));
        when(userRepository.existsByPhone(anyString())).thenAnswer(inv ->
                users.values().stream().anyMatch(u -> u.getPhone().equals(inv.getArgument(0, String.class))));
        when(userRepository.findByEmailIgnoreCase(anyString())).thenAnswer(inv ->
                users.values().stream().filter(u -> u.getEmail().equalsIgnoreCase(inv.getArgument(0, String.class))).findFirst());
        when(userRepository.findById(anyLong())).thenAnswer(inv ->
                Optional.ofNullable(users.get(inv.getArgument(0, Long.class))));
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

        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> {
            Task t = inv.getArgument(0);
            if (t.getId() == null) t.setId(seq.getAndIncrement());
            tasks.put(t.getId(), t);
            return t;
        });
        when(taskRepository.findAllByUserIdAndTaskDateAndDeletedAtUtcIsNullOrderByPriorityAscStartTimeAscIdAsc(
                anyLong(), any(LocalDate.class)))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0, Long.class);
                    LocalDate date = inv.getArgument(1, LocalDate.class);
                    return tasks.values().stream()
                            .filter(t -> t.getUserId().equals(uid) && t.getTaskDate().equals(date) && t.getDeletedAtUtc() == null)
                            .toList();
                });
        when(taskRepository.findAllByUserIdAndTaskDateBetweenAndDeletedAtUtcIsNull(anyLong(), any(), any()))
                .thenReturn(List.of());
        when(taskRepository.findAllByUserIdAndTaskDateBeforeAndStatusInAndDeletedAtUtcIsNullOrderByTaskDateDescPriorityAscIdAsc(
                anyLong(), any(), anyCollection()))
                .thenReturn(List.of());
        when(taskRepository.findAllByUserIdAndDeletedAtUtcIsNullAndTaskDateLessThanAndStatusInOrderByTaskDateAscStartTimeAscIdAsc(
                anyLong(), any(), anyCollection()))
                .thenReturn(List.of());
        when(taskRepository.findAllByUserIdAndDeletedAtUtcIsNullAndTaskDateBetweenOrderByTaskDateAscStartTimeAscIdAsc(
                anyLong(), any(), any()))
                .thenReturn(List.of());

        when(codingActivityRepository.findAllByUserIdAndDeletedAtUtcIsNullOrderByActivityDateDescIdDesc(anyLong()))
                .thenReturn(List.of());
        when(codingActivityRepository.findAllByUserIdAndDeletedAtUtcIsNullAndActivityDateBetweenOrderByActivityDateDescIdDesc(
                anyLong(), any(), any()))
                .thenReturn(List.of());
        when(learningRecordRepository.findAllByUserIdAndRecordDateBetweenOrderByRecordDateAscIdAsc(anyLong(), any(), any()))
                .thenReturn(List.of());
        when(projectWorklogRepository.findAllByUserIdAndDeletedAtUtcIsNullAndWorkDateBetweenOrderByWorkDateDescIdDesc(
                anyLong(), any(), any()))
                .thenReturn(List.of());
        when(projectMilestoneRepository.findAllByUserIdAndDeletedAtUtcIsNullAndPlannedDateBetweenOrderByPlannedDateAscIdAsc(
                anyLong(), any(), any()))
                .thenReturn(List.of());
        when(jobFollowupRepository.findAllByUserIdAndDoneFalseAndFollowupDateBetweenOrderByFollowupDateAscIdAsc(
                anyLong(), any(), any()))
                .thenReturn(List.of());
        when(goalRepository.findAllByUserIdAndGoalTypeAndPeriodStartDateBetweenOrderByPeriodStartDateDescIdDesc(
                anyLong(), anyString(), any(), any()))
                .thenReturn(List.of());

        when(projectRepository.findAllByUserIdAndDeletedAtUtcIsNullOrderByUpdatedAtUtcDescIdDesc(anyLong()))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0, Long.class);
                    return projects.values().stream().filter(p -> p.getUserId().equals(uid)).toList();
                });

        when(jobApplicationRepository.findByIdAndUserIdAndDeletedAtUtcIsNull(anyLong(), anyLong()))
                .thenAnswer(inv -> {
                    Long id = inv.getArgument(0, Long.class);
                    Long uid = inv.getArgument(1, Long.class);
                    JobApplication j = jobs.get(id);
                    return (j != null && j.getUserId().equals(uid)) ? Optional.of(j) : Optional.empty();
                });
        when(jobApplicationRepository.save(any(JobApplication.class))).thenAnswer(inv -> {
            JobApplication j = inv.getArgument(0);
            if (j.getId() == null) j.setId(seq.getAndIncrement());
            jobs.put(j.getId(), j);
            return j;
        });

        when(aiProposalRepository.save(any(AiProposal.class))).thenAnswer(inv -> {
            AiProposal p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(seq.getAndIncrement());
                p.setCreatedAtUtc(Instant.now());
            }
            proposals.put(p.getId(), p);
            return p;
        });
        when(aiProposalRepository.findByIdAndUserId(anyLong(), anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0, Long.class);
            Long uid = inv.getArgument(1, Long.class);
            AiProposal p = proposals.get(id);
            return (p != null && p.getUserId().equals(uid)) ? Optional.of(p) : Optional.empty();
        });
        when(aiProposalRepository.findAllByUserIdAndStatusOrderByCreatedAtUtcDescIdDesc(anyLong(), anyString()))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0, Long.class);
                    String status = inv.getArgument(1, String.class);
                    return proposals.values().stream()
                            .filter(p -> p.getUserId().equals(uid) && status.equalsIgnoreCase(p.getStatus()))
                            .toList();
                });

        when(monthlyReportRepository.findByUserIdAndReportMonth(anyLong(), anyString())).thenReturn(Optional.empty());
        when(monthlyReportRepository.save(any(MonthlyReport.class))).thenAnswer(inv -> inv.getArgument(0));
        when(recordHistoryRepository.save(any(RecordHistory.class))).thenAnswer(inv -> {
            RecordHistory rh = inv.getArgument(0);
            histories.add(rh);
            return rh;
        });
    }

    private Cookie registerAndGetCookie(String email, String phone) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Alex Dev",
                                  "email": "%s",
                                  "phone": "%s",
                                  "password": "Password123",
                                  "confirmPassword": "Password123"
                                }
                                """.formatted(email, phone)))
                .andExpect(status().isCreated())
                .andReturn();
        return res.getResponse().getCookie("pathpilot_token");
    }

    @Test
    void recommendationAndProposalApprovalWorkflowRespectsUserIsolation() throws Exception {
        Cookie ownerCookie = registerAndGetCookie("aiowner@example.com", "9111111111");
        Cookie otherCookie = registerAndGetCookie("aiother@example.com", "9222222222");

        when(llmClient.isConfigured()).thenReturn(true);
        when(llmClient.generate(any(), any())).thenReturn(new LlmResult("""
                {
                  "recommendation": "You have not recorded coding practice yet. Solve one Java array problem today.",
                  "proposedTaskTitle": "Solve 1 Java array problem today",
                  "proposedTaskCategory": "DSA_PRACTICE",
                  "actionLabel": "Log coding",
                  "actionPath": "/app/coding"
                }
                """, "gemini-3.5-flash-lite", "gemini"));

        mockMvc.perform(get("/api/ai/recommendation?date=2026-11-27").cookie(ownerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("AI"))
                .andExpect(jsonPath("$.disclaimer").value("AI-generated. Based on your saved records."))
                .andExpect(jsonPath("$.proposedTask.title").value("Solve 1 Java array problem today"));

        // Creating proposal does NOT create any real Task yet
        MvcResult proposalRes = mockMvc.perform(post("/api/ai/proposals/task")
                        .cookie(ownerCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Solve 1 Java array problem today",
                                  "category": "DSA_PRACTICE",
                                  "taskDate": "2026-11-27",
                                  "priority": "HIGH"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        JsonNode proposalJson = objectMapper.readTree(proposalRes.getResponse().getContentAsString());
        long proposalId = proposalJson.get("id").asLong();
        org.junit.jupiter.api.Assertions.assertTrue(tasks.isEmpty());

        // Another user cannot approve owner's proposal
        mockMvc.perform(post("/api/ai/proposals/" + proposalId + "/approve").cookie(otherCookie))
                .andExpect(status().isNotFound());

        // Owner approves -> real Task + RecordHistory are created
        mockMvc.perform(post("/api/ai/proposals/" + proposalId + "/approve").cookie(ownerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        org.junit.jupiter.api.Assertions.assertEquals(1, tasks.size());
        org.junit.jupiter.api.Assertions.assertEquals("AI", tasks.values().iterator().next().getDataSource());
    }

    @Test
    void jdAnalysisPersistsResultAndEnforcesProfileAndProjectGrounding() throws Exception {
        Cookie cookie = registerAndGetCookie("jduser@example.com", "9333333333");
        User owner = users.values().iterator().next();

        UserProfile profile = profiles.get(owner.getId());
        profile.setSkillsJson("[{\"name\":\"Java\",\"level\":\"Comfortable\"},{\"name\":\"SQL\",\"level\":\"Beginner\"}]");

        Project p = new Project();
        p.setId(50L);
        p.setUserId(owner.getId());
        p.setTitle("PathPilot");
        p.setTechStack("Java, Spring Boot, React");
        projects.put(50L, p);

        JobApplication job = new JobApplication();
        job.setId(80L);
        job.setUserId(owner.getId());
        job.setCompanyName("Acme Systems");
        job.setRoleTitle("Junior Java Developer");
        job.setJobDescription("<script>alert(1)</script>Looking for Java, Spring Boot, and SQL.");
        jobs.put(80L, job);

        when(llmClient.isConfigured()).thenReturn(false);

        mockMvc.perform(post("/api/ai/jd-analysis")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "jobApplicationId": 80 }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("RULE"))
                .andExpect(jsonPath("$.matchedSkills[0]").value("Java"))
                .andExpect(jsonPath("$.weakAreas[0]").value("SQL"))
                .andExpect(jsonPath("$.recommendedProjectsToShow[0]").value("PathPilot"));

        mockMvc.perform(get("/api/ai/jd-analysis/80").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobApplicationId").value(80));
    }

    @Test
    void monthlySummaryAndRateLimitingWorkAsExpected() throws Exception {
        Cookie cookie = registerAndGetCookie("ratelimit@example.com", "9444444444");
        when(llmClient.isConfigured()).thenReturn(false);

        mockMvc.perform(post("/api/ai/monthly-summary/2026-11").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-11"))
                .andExpect(jsonPath("$.source").value("RULE"));

        // Local profile ai.rateLimitMax defaults to 30; hitting endpoint repeatedly returns 429 cleanly
        for (int i = 0; i < 29; i++) {
            mockMvc.perform(get("/api/ai/recommendation?date=2026-11-27").cookie(cookie))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/ai/recommendation?date=2026-11-27").cookie(cookie))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AI_RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message", containsString("rate limit")));
    }
}
