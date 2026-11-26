package com.pathpilot.progress;

import com.pathpilot.coding.CodingActivityRepository;
import com.pathpilot.github.GithubEventRepository;
import com.pathpilot.job.JobApplicationRepository;
import com.pathpilot.planner.DailyLog;
import com.pathpilot.planner.DailyLogRepository;
import com.pathpilot.planner.Goal;
import com.pathpilot.planner.GoalRepository;
import com.pathpilot.planner.LearningRecordRepository;
import com.pathpilot.planner.RecordHistoryRepository;
import com.pathpilot.planner.Task;
import com.pathpilot.planner.TaskRepository;
import com.pathpilot.planner.WeeklyReview;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

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
class WeeklyMonthlyIntegrationTest {

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
    @MockitoBean private CodingActivityRepository codingActivityRepository;
    @MockitoBean private GithubEventRepository githubEventRepository;
    @MockitoBean private ProjectRepository projectRepository;
    @MockitoBean private ProjectMilestoneRepository projectMilestoneRepository;
    @MockitoBean private JobApplicationRepository jobApplicationRepository;

    private MockMvc mockMvc;
    private final Map<Long, User> users = new ConcurrentHashMap<>();
    private final Map<Long, UserProfile> profiles = new ConcurrentHashMap<>();
    private final List<Task> tasks = new CopyOnWriteArrayList<>();
    private final List<Goal> goals = new CopyOnWriteArrayList<>();
    private final List<WeeklyReview> reviews = new CopyOnWriteArrayList<>();
    private final AtomicLong seq = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        authRateLimitFilter.clearAll();
        users.clear();
        profiles.clear();
        tasks.clear();
        goals.clear();
        reviews.clear();

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

        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> {
            Task t = inv.getArgument(0);
            if (t.getId() == null) t.setId(seq.getAndIncrement());
            tasks.add(t);
            return t;
        });
        when(taskRepository.findAllByUserIdAndTaskDateAndDeletedAtUtcIsNullOrderByPriorityAscStartTimeAscIdAsc(
                anyLong(), any(LocalDate.class)))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0, Long.class);
                    LocalDate date = inv.getArgument(1, LocalDate.class);
                    return tasks.stream().filter(t -> t.getUserId().equals(uid) && t.getTaskDate().equals(date)).toList();
                });
        when(taskRepository.findAllByUserIdAndTaskDateBetweenAndDeletedAtUtcIsNull(
                anyLong(), any(LocalDate.class), any(LocalDate.class)))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0, Long.class);
                    LocalDate start = inv.getArgument(1, LocalDate.class);
                    LocalDate end = inv.getArgument(2, LocalDate.class);
                    return tasks.stream().filter(t -> t.getUserId().equals(uid)
                            && !t.getTaskDate().isBefore(start) && !t.getTaskDate().isAfter(end)).toList();
                });

        when(goalRepository.save(any(Goal.class))).thenAnswer(inv -> {
            Goal g = inv.getArgument(0);
            if (g.getId() == null) g.setId(seq.getAndIncrement());
            goals.removeIf(existing -> existing.getId().equals(g.getId()));
            goals.add(g);
            return g;
        });
        when(goalRepository.findAllByUserIdAndPeriodTypeAndStartDateAndDeletedAtUtcIsNullOrderByIdAsc(
                anyLong(), anyString(), any(LocalDate.class)))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0, Long.class);
                    String scope = inv.getArgument(1, String.class);
                    LocalDate start = inv.getArgument(2, LocalDate.class);
                    return goals.stream().filter(g -> g.getUserId().equals(uid)
                            && g.getPeriodType().equals(scope) && g.getStartDate().equals(start)).toList();
                });

        when(weeklyReviewRepository.save(any(WeeklyReview.class))).thenAnswer(inv -> {
            WeeklyReview r = inv.getArgument(0);
            if (r.getId() == null) r.setId(seq.getAndIncrement());
            reviews.removeIf(existing -> existing.getId().equals(r.getId()));
            reviews.add(r);
            return r;
        });
        when(weeklyReviewRepository.findByUserIdAndWeekStartDate(anyLong(), any(LocalDate.class)))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0, Long.class);
                    LocalDate start = inv.getArgument(1, LocalDate.class);
                    return reviews.stream().filter(r -> r.getUserId().equals(uid) && r.getWeekStartDate().equals(start)).findFirst();
                });

        when(dailyLogRepository.findByUserIdAndLogDate(anyLong(), any(LocalDate.class))).thenReturn(Optional.empty());
        when(dailyLogRepository.save(any(DailyLog.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void weeklyAndMonthlyProgress_matchDashboardAndKeepPeriodsSeparate() throws Exception {
        Cookie cookie = registerAndLogin("Riya Dev", "9876500001", "riya@example.com");

        // Create 3 tasks in week 2026-11-23..29 (2 COMPLETED, 1 PENDING => 66.7%)
        createTask(cookie, "Java Streams", "2026-11-23", "JAVA_LEARNING", "COMPLETED");
        createTask(cookie, "Spring Security", "2026-11-25", "SPRING_BOOT", "COMPLETED");
        createTask(cookie, "Binary Search", "2026-11-27", "CODING_PRACTICE", "PENDING");

        mockMvc.perform(get("/api/progress/weekly?start=2026-11-25").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekStart").value("2026-11-23"))
                .andExpect(jsonPath("$.weekEnd").value("2026-11-29"))
                .andExpect(jsonPath("$.plannedCount").value(3))
                .andExpect(jsonPath("$.completedCount").value(2))
                .andExpect(jsonPath("$.completionRate").value(66.7));

        // Next week stays empty (never overwrites or bleeds into 2026-11-30)
        mockMvc.perform(get("/api/progress/weekly?start=2026-11-30").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plannedCount").value(0))
                .andExpect(jsonPath("$.completionRate").doesNotExist());

        // Monthly progress matches Dashboard monthly progress (66.7%), and previous month (Oct) has no data => null change
        mockMvc.perform(get("/api/progress/monthly?month=2026-11").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completionRate").value(66.7))
                .andExpect(jsonPath("$.changeVsPreviousMonth").doesNotExist());

        mockMvc.perform(get("/api/progress/dashboard?date=2026-11-25").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthly.completionRate").value(66.7));

        // Save weekly review for 2026-11-23
        mockMvc.perform(post("/api/planner/weekly-review")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "weekStartDate": "2026-11-23",
                                  "whatWentWell": "Finished Spring Security",
                                  "carryForwardNotes": "Binary Search practice"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.whatWentWell").value("Finished Spring Security"));
    }

    private void createTask(Cookie cookie, String title, String date, String category, String status) throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", title,
                                "taskDate", date,
                                "category", category,
                                "status", status,
                                "priority", "MEDIUM"
                        ))))
                .andExpect(status().isCreated());
    }

    private Cookie registerAndLogin(String name, String phone, String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", name,
                                "phone", phone,
                                "email", email,
                                "password", "Password123",
                                "confirmPassword", "Password123"
                        ))))
                .andExpect(status().isCreated());

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", email,
                                "password", "Password123"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        String setCookie = login.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        String token = setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
        return new Cookie("pathpilot_token", token);
    }
}
