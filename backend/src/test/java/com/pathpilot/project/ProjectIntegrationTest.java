package com.pathpilot.project;

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
import com.pathpilot.planner.RecordHistory;
import com.pathpilot.planner.RecordHistoryRepository;
import com.pathpilot.planner.TaskRepository;
import com.pathpilot.planner.WeeklyReviewRepository;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class ProjectIntegrationTest {

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
    private final Map<Long, Project> projects = new ConcurrentHashMap<>();
    private final Map<Long, ProjectMilestone> milestones = new ConcurrentHashMap<>();
    private final Map<Long, ProjectWorklog> worklogs = new ConcurrentHashMap<>();
    private final List<RecordHistory> histories = new CopyOnWriteArrayList<>();
    private final AtomicLong seq = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        authRateLimitFilter.clearAll();
        users.clear();
        profiles.clear();
        projects.clear();
        milestones.clear();
        worklogs.clear();
        histories.clear();

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

        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> {
            Project p = inv.getArgument(0);
            if (p.getId() == null) p.setId(seq.getAndIncrement());
            p.setCreatedAtUtc(Instant.now());
            p.setUpdatedAtUtc(Instant.now());
            projects.put(p.getId(), p);
            return p;
        });
        when(projectRepository.findByIdAndUserIdAndDeletedAtUtcIsNull(anyLong(), anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            Long uid = inv.getArgument(1);
            Project p = projects.get(id);
            if (p == null || !p.getUserId().equals(uid) || p.getDeletedAtUtc() != null) return Optional.empty();
            return Optional.of(p);
        });
        when(projectRepository.findAllByUserIdAndDeletedAtUtcIsNullOrderByUpdatedAtUtcDescIdDesc(anyLong())).thenAnswer(inv -> {
            Long uid = inv.getArgument(0);
            return projects.values().stream()
                    .filter(p -> p.getUserId().equals(uid) && p.getDeletedAtUtc() == null)
                    .toList();
        });

        when(projectMilestoneRepository.save(any(ProjectMilestone.class))).thenAnswer(inv -> {
            ProjectMilestone m = inv.getArgument(0);
            if (m.getId() == null) m.setId(seq.getAndIncrement());
            milestones.put(m.getId(), m);
            return m;
        });
        when(projectMilestoneRepository.findAllByUserIdAndProjectIdAndDeletedAtUtcIsNullOrderBySortOrderAscIdAsc(anyLong(), anyLong()))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0);
                    Long pid = inv.getArgument(1);
                    return milestones.values().stream()
                            .filter(m -> m.getUserId().equals(uid) && m.getProjectId().equals(pid) && m.getDeletedAtUtc() == null)
                            .toList();
                });
        when(projectMilestoneRepository.findByIdAndUserIdAndProjectIdAndDeletedAtUtcIsNull(anyLong(), anyLong(), anyLong()))
                .thenAnswer(inv -> {
                    Long id = inv.getArgument(0);
                    Long uid = inv.getArgument(1);
                    Long pid = inv.getArgument(2);
                    ProjectMilestone m = milestones.get(id);
                    if (m == null || !m.getUserId().equals(uid) || !m.getProjectId().equals(pid) || m.getDeletedAtUtc() != null) {
                        return Optional.empty();
                    }
                    return Optional.of(m);
                });

        when(projectWorklogRepository.save(any(ProjectWorklog.class))).thenAnswer(inv -> {
            ProjectWorklog w = inv.getArgument(0);
            if (w.getId() == null) w.setId(seq.getAndIncrement());
            worklogs.put(w.getId(), w);
            return w;
        });
        when(projectWorklogRepository.findAllByUserIdAndProjectIdAndDeletedAtUtcIsNullOrderByWorkDateDescIdDesc(anyLong(), anyLong()))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0);
                    Long pid = inv.getArgument(1);
                    return worklogs.values().stream()
                            .filter(w -> w.getUserId().equals(uid) && w.getProjectId().equals(pid) && w.getDeletedAtUtc() == null)
                            .toList();
                });
        when(projectWorklogRepository.findAllByUserIdAndWorkDateAndDeletedAtUtcIsNullOrderByCreatedAtUtcDescIdDesc(anyLong(), any(LocalDate.class)))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0);
                    LocalDate d = inv.getArgument(1);
                    return worklogs.values().stream()
                            .filter(w -> w.getUserId().equals(uid) && w.getWorkDate().equals(d) && w.getDeletedAtUtc() == null)
                            .toList();
                });
        when(projectWorklogRepository.findAllByUserIdAndDeletedAtUtcIsNullAndWorkDateBetweenOrderByWorkDateDescIdDesc(
                anyLong(), any(LocalDate.class), any(LocalDate.class)))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0);
                    LocalDate s = inv.getArgument(1);
                    LocalDate e = inv.getArgument(2);
                    return worklogs.values().stream()
                            .filter(w -> w.getUserId().equals(uid) && w.getDeletedAtUtc() == null
                                    && !w.getWorkDate().isBefore(s) && !w.getWorkDate().isAfter(e))
                            .toList();
                });

        when(recordHistoryRepository.save(any(RecordHistory.class))).thenAnswer(inv -> {
            RecordHistory h = inv.getArgument(0);
            histories.add(h);
            return h;
        });
        when(recordHistoryRepository.findAllByUserIdAndEntityTypeAndEntityIdOrderByCreatedAtUtcDescIdDesc(
                anyLong(), anyString(), anyLong())).thenAnswer(inv -> histories);
    }

    @Test
    void pathPilotProjectFlow_updatesDashboardAndIsolatesUsersAndFeedsDayAndMonthly() throws Exception {
        Cookie user1Cookie = registerAndLogin("user1@example.com", "9876543211");
        Cookie user2Cookie = registerAndLogin("user2@example.com", "9876543212");

        // Create PathPilot as CURRENT project
        MvcResult createRes = mockMvc.perform(post("/api/projects")
                        .cookie(user1Cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "PathPilot",
                                "status", "CURRENT",
                                "techStack", List.of("Java", "Spring Boot", "React"),
                                "repoUrl", "https://github.com/user1/pathpilot"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.project.title").value("PathPilot"))
                .andExpect(jsonPath("$.project.progress").doesNotExist())
                .andReturn();

        JsonNode createdJson = objectMapper.readTree(createRes.getResponse().getContentAsString());
        long projectId = createdJson.path("project").path("id").asLong();

        // Add 4 milestones, complete 1 -> 25%
        long firstMilestoneId = -1;
        for (int i = 1; i <= 4; i++) {
            MvcResult msRes = mockMvc.perform(post("/api/projects/" + projectId + "/milestones")
                            .cookie(user1Cookie)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("title", "Milestone " + i))))
                    .andExpect(status().isCreated())
                    .andReturn();
            if (i == 1) {
                firstMilestoneId = objectMapper.readTree(msRes.getResponse().getContentAsString()).path("id").asLong();
            }
        }

        mockMvc.perform(patch("/api/projects/" + projectId + "/milestones/" + firstMilestoneId + "/complete")
                        .cookie(user1Cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(get("/api/projects").cookie(user1Cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("PathPilot"))
                .andExpect(jsonPath("$[0].progress").value(25.0));

        // Dashboard Project Progress card shows 25%
        mockMvc.perform(get("/api/progress/dashboard").cookie(user1Cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentProject.name").value("PathPilot"))
                .andExpect(jsonPath("$.currentProject.progress").value(25.0));

        // User 2 cannot open User 1's project by changing the id in URL
        mockMvc.perform(get("/api/projects/" + projectId).cookie(user2Cookie))
                .andExpect(status().isNotFound());

        // Work log appears in Coding day drawer and Monthly progress
        mockMvc.perform(post("/api/projects/" + projectId + "/worklogs")
                        .cookie(user1Cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "workDate", "2026-11-27",
                                "minutesSpent", 75,
                                "summary", "Built ProjectsPage and drawer"
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/coding/day/2026-11-27").cookie(user1Cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectWorklogs[0].projectTitle").value("PathPilot"))
                .andExpect(jsonPath("$.projectWorklogs[0].summary").value("Built ProjectsPage and drawer"));

        mockMvc.perform(get("/api/progress/monthly?month=2026-11").cookie(user1Cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectWorklogs[0].projectTitle").value("PathPilot"));

        // Profile links & settings update
        mockMvc.perform(put("/api/profile")
                        .cookie(user1Cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "timeZone", "Asia/Kolkata",
                                "portfolioUrl", "https://user1.dev",
                                "githubInput", "https://github.com/user1/pathpilot",
                                "leetcodeUrl", "https://leetcode.com/u/user1"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portfolioUrl").value("https://user1.dev"))
                .andExpect(jsonPath("$.githubUrl").value("https://github.com/user1/pathpilot"))
                .andExpect(jsonPath("$.leetcodeUrl").value("https://leetcode.com/u/user1"));
    }

    private Cookie registerAndLogin(String email, String phone) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Test User",
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
