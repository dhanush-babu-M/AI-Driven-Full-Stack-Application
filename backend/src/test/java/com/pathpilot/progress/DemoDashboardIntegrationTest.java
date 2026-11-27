package com.pathpilot.progress;

import com.pathpilot.coding.CodingActivity;
import com.pathpilot.coding.CodingActivityRepository;
import com.pathpilot.github.ConnectedAccountRepository;
import com.pathpilot.github.GithubEvent;
import com.pathpilot.github.GithubEventRepository;
import com.pathpilot.job.JobApplication;
import com.pathpilot.job.JobApplicationRepository;
import com.pathpilot.planner.DailyLogRepository;
import com.pathpilot.planner.GoalRepository;
import com.pathpilot.planner.LearningRecordRepository;
import com.pathpilot.planner.NotificationPreferencesRepository;
import com.pathpilot.planner.RecordHistoryRepository;
import com.pathpilot.planner.Task;
import com.pathpilot.planner.TaskRepository;
import com.pathpilot.planner.WeeklyReviewRepository;
import com.pathpilot.project.Project;
import com.pathpilot.project.ProjectMediaRepository;
import com.pathpilot.project.ProjectMilestone;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

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
class DemoDashboardIntegrationTest {

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

    private MockMvc mockMvc;
    private final Map<Long, User> users = new ConcurrentHashMap<>();
    private final Map<Long, UserProfile> profiles = new ConcurrentHashMap<>();
    private final List<Task> tasks = new CopyOnWriteArrayList<>();
    private final List<CodingActivity> codings = new CopyOnWriteArrayList<>();
    private final List<GithubEvent> githubEvents = new CopyOnWriteArrayList<>();
    private final List<Project> projects = new CopyOnWriteArrayList<>();
    private final List<ProjectMilestone> milestones = new CopyOnWriteArrayList<>();
    private final List<JobApplication> applications = new CopyOnWriteArrayList<>();
    private final AtomicLong seq = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        authRateLimitFilter.clearAll();

        when(userRepository.existsByEmailIgnoreCase(anyString())).thenAnswer(inv ->
                users.values().stream().anyMatch(u -> u.getEmail().equalsIgnoreCase(inv.getArgument(0, String.class))));
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

        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> {
            Task t = inv.getArgument(0);
            if (t.getId() == null) t.setId(seq.getAndIncrement());
            tasks.add(t);
            return t;
        });
        when(taskRepository.findAllByUserIdAndTaskDateAndDeletedAtUtcIsNullOrderByPriorityAscStartTimeAscIdAsc(anyLong(), any(LocalDate.class)))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0, Long.class);
                    LocalDate date = inv.getArgument(1, LocalDate.class);
                    return tasks.stream().filter(t -> t.getUserId().equals(uid) && t.getTaskDate().equals(date)).toList();
                });
        when(taskRepository.findAllByUserIdAndTaskDateBetweenAndDeletedAtUtcIsNull(anyLong(), any(LocalDate.class), any(LocalDate.class)))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0, Long.class);
                    LocalDate start = inv.getArgument(1, LocalDate.class);
                    LocalDate end = inv.getArgument(2, LocalDate.class);
                    return tasks.stream().filter(t -> t.getUserId().equals(uid)
                            && !t.getTaskDate().isBefore(start) && !t.getTaskDate().isAfter(end)).toList();
                });

        when(codingActivityRepository.save(any(CodingActivity.class))).thenAnswer(inv -> {
            CodingActivity c = inv.getArgument(0);
            codings.add(c);
            return c;
        });
        when(codingActivityRepository.findAllByUserIdAndDeletedAtUtcIsNullAndActivityDateBetweenOrderByActivityDateDescIdDesc(
                anyLong(), any(LocalDate.class), any(LocalDate.class)))
                .thenAnswer(inv -> codings);

        when(githubEventRepository.save(any(GithubEvent.class))).thenAnswer(inv -> {
            GithubEvent g = inv.getArgument(0);
            githubEvents.add(g);
            return g;
        });
        when(githubEventRepository.findAllByUserIdAndEventDateBetween(anyLong(), any(LocalDate.class), any(LocalDate.class)))
                .thenAnswer(inv -> githubEvents);

        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> {
            Project p = inv.getArgument(0);
            try {
                var f = Project.class.getDeclaredField("id");
                f.setAccessible(true);
                if (f.get(p) == null) f.set(p, seq.getAndIncrement());
            } catch (Exception ignored) {}
            projects.add(p);
            return p;
        });
        when(projectRepository.findFirstByUserIdOrderByUpdatedAtUtcDescIdDesc(anyLong()))
                .thenAnswer(inv -> projects.stream().findFirst());
        when(projectRepository.findAllByUserIdAndDeletedAtUtcIsNullOrderByUpdatedAtUtcDescIdDesc(anyLong()))
                .thenAnswer(inv -> projects);

        when(projectMilestoneRepository.save(any(ProjectMilestone.class))).thenAnswer(inv -> {
            ProjectMilestone m = inv.getArgument(0);
            milestones.add(m);
            return m;
        });
        when(projectMilestoneRepository.findAllByUserIdAndProjectIdOrderBySortOrderAscIdAsc(anyLong(), anyLong()))
                .thenAnswer(inv -> milestones);
        when(projectMilestoneRepository.findAllByUserIdAndProjectIdAndDeletedAtUtcIsNullOrderBySortOrderAscIdAsc(anyLong(), anyLong()))
                .thenAnswer(inv -> milestones);

        when(jobApplicationRepository.save(any(JobApplication.class))).thenAnswer(inv -> {
            JobApplication a = inv.getArgument(0);
            try {
                var f = JobApplication.class.getDeclaredField("id");
                f.setAccessible(true);
                if (f.get(a) == null) f.set(a, seq.getAndIncrement());
            } catch (Exception ignored) {}
            applications.add(a);
            return a;
        });
        when(jobApplicationRepository.findTop3ByUserIdOrderByAppliedDateDescIdDesc(anyLong()))
                .thenAnswer(inv -> applications.stream().limit(3).toList());
    }

    @Test
    void demoProfile_seedsAndComputesExactDashboardMath() throws Exception {
        DemoDataSeeder seeder = new DemoDataSeeder(
                userRepository, userProfileRepository, taskRepository,
                codingActivityRepository, githubEventRepository,
                projectRepository, projectMilestoneRepository,
                jobApplicationRepository,
                context.getBean(org.springframework.security.crypto.password.PasswordEncoder.class)
        );
        seeder.run();

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "identifier": "demo@pathpilot.local",
                                  "password": "DemoUser123"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String setCookie = loginResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        String token = setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));

        mockMvc.perform(get("/api/progress/dashboard")
                        .cookie(new Cookie("pathpilot_token", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demo").value(true))
                .andExpect(jsonPath("$.greetingName").value("Aarav"))
                .andExpect(jsonPath("$.today.plannedCount").value(7))
                .andExpect(jsonPath("$.today.completedCount").value(4))
                .andExpect(jsonPath("$.today.rescheduledCount").value(1))
                .andExpect(jsonPath("$.today.completionRate").value(57.1))
                .andExpect(jsonPath("$.today.categoryRates.learning").value(50.0))
                .andExpect(jsonPath("$.today.categoryRates.practice").value(100.0))
                .andExpect(jsonPath("$.today.categoryRates.projects").value(50.0))
                .andExpect(jsonPath("$.today.categoryRates.applications").value(0.0))
                .andExpect(jsonPath("$.codingStreak.days").value(5))
                .andExpect(jsonPath("$.currentProject.progress").value(75.0))
                .andExpect(jsonPath("$.recentApplications.length()").value(3))
                .andExpect(jsonPath("$.hasGithubActivity").value(true));
    }
}
