package com.pathpilot.planner;

import com.pathpilot.coding.CodingActivityRepository;
import com.pathpilot.github.GithubEventRepository;
import com.pathpilot.job.JobApplicationRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class PlannerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AuthRateLimitFilter authRateLimitFilter;

    @Autowired
    private ObjectMapper objectMapper;

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
    private CodingActivityRepository codingActivityRepository;

    @MockitoBean
    private GithubEventRepository githubEventRepository;

    @MockitoBean
    private ProjectRepository projectRepository;

    @MockitoBean
    private ProjectMilestoneRepository projectMilestoneRepository;

    @MockitoBean
    private JobApplicationRepository jobApplicationRepository;

    private MockMvc mockMvc;
    private final Map<Long, User> users = new ConcurrentHashMap<>();
    private final Map<Long, UserProfile> profiles = new ConcurrentHashMap<>();
    private final Map<Long, Task> tasksById = new ConcurrentHashMap<>();
    private final List<DailyLog> dailyLogs = new CopyOnWriteArrayList<>();
    private final List<RecordHistory> histories = new CopyOnWriteArrayList<>();
    private final AtomicLong seq = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        authRateLimitFilter.clearAll();
        users.clear();
        profiles.clear();
        tasksById.clear();
        dailyLogs.clear();
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
            tasksById.put(t.getId(), t);
            return t;
        });
        when(taskRepository.findByIdAndUserIdAndDeletedAtUtcIsNull(anyLong(), anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0, Long.class);
            Long uid = inv.getArgument(1, Long.class);
            Task t = tasksById.get(id);
            if (t == null || !t.getUserId().equals(uid) || t.getDeletedAtUtc() != null) {
                return Optional.empty();
            }
            return Optional.of(t);
        });
        when(taskRepository.findAllByUserIdAndTaskDateAndDeletedAtUtcIsNullOrderByPriorityAscStartTimeAscIdAsc(
                anyLong(), any(LocalDate.class)))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0, Long.class);
                    LocalDate date = inv.getArgument(1, LocalDate.class);
                    return tasksById.values().stream()
                            .filter(t -> t.getUserId().equals(uid) && t.getTaskDate().equals(date) && t.getDeletedAtUtc() == null)
                            .toList();
                });
        when(taskRepository.findAllByUserIdAndTaskDateBetweenAndDeletedAtUtcIsNull(
                anyLong(), any(LocalDate.class), any(LocalDate.class)))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0, Long.class);
                    LocalDate start = inv.getArgument(1, LocalDate.class);
                    LocalDate end = inv.getArgument(2, LocalDate.class);
                    return tasksById.values().stream()
                            .filter(t -> t.getUserId().equals(uid) && t.getDeletedAtUtc() == null
                                    && !t.getTaskDate().isBefore(start) && !t.getTaskDate().isAfter(end))
                            .toList();
                });
        when(taskRepository.findAllByUserIdAndTaskDateBeforeAndStatusInAndDeletedAtUtcIsNullOrderByTaskDateDescPriorityAscIdAsc(
                anyLong(), any(LocalDate.class), anyCollection()))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0, Long.class);
                    LocalDate before = inv.getArgument(1, LocalDate.class);
                    @SuppressWarnings("unchecked")
                    var statuses = (java.util.Collection<String>) inv.getArgument(2);
                    return tasksById.values().stream()
                            .filter(t -> t.getUserId().equals(uid) && t.getDeletedAtUtc() == null
                                    && t.getTaskDate().isBefore(before) && statuses.contains(t.getStatus()))
                            .toList();
                });

        when(dailyLogRepository.findByUserIdAndLogDate(anyLong(), any(LocalDate.class))).thenAnswer(inv -> {
            Long uid = inv.getArgument(0, Long.class);
            LocalDate date = inv.getArgument(1, LocalDate.class);
            return dailyLogs.stream().filter(l -> l.getUserId().equals(uid) && l.getLogDate().equals(date)).findFirst();
        });
        when(dailyLogRepository.save(any(DailyLog.class))).thenAnswer(inv -> {
            DailyLog l = inv.getArgument(0);
            if (l.getId() == null) l.setId(seq.getAndIncrement());
            dailyLogs.removeIf(existing -> existing.getId().equals(l.getId()));
            dailyLogs.add(l);
            return l;
        });

        when(recordHistoryRepository.save(any(RecordHistory.class))).thenAnswer(inv -> {
            RecordHistory h = inv.getArgument(0);
            if (h.getId() == null) h.setId(seq.getAndIncrement());
            histories.add(h);
            return h;
        });
        when(recordHistoryRepository.findAllByUserIdAndEntityTypeAndEntityIdOrderByCreatedAtUtcDescIdDesc(
                anyLong(), anyString(), anyLong()))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0, Long.class);
                    String type = inv.getArgument(1, String.class);
                    Long eid = inv.getArgument(2, Long.class);
                    return histories.stream()
                            .filter(h -> h.getUserId().equals(uid) && h.getEntityType().equals(type) && h.getEntityId().equals(eid))
                            .toList();
                });
    }

    @Test
    void breakfastTask_carryForward_historyAndUserIsolation_workEndToEnd() throws Exception {
        Cookie userACookie = registerAndLogin("Alex User", "9876543210", "alex@example.com");
        Cookie userBCookie = registerAndLogin("Priya User", "9876543211", "priya@example.com");

        LocalDate today = LocalDate.of(2026, 11, 26);
        LocalDate yesterday = today.minusDays(1);

        // 1. Create breakfast task for today and mark Completed -> dashboard ring = 100%
        MvcResult createBreakfast = mockMvc.perform(post("/api/tasks")
                        .cookie(userACookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Eat breakfast - puri",
                                  "category": "MEALS_PERSONAL",
                                  "taskDate": "2026-11-26",
                                  "startTime": "08:30",
                                  "endTime": "09:00",
                                  "priority": "MEDIUM",
                                  "status": "PLANNED"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.colorGroup").value("Health"))
                .andExpect(jsonPath("$.durationMinutes").value(30))
                .andReturn();

        Long breakfastId = objectMapper.readTree(createBreakfast.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/api/tasks/" + breakfastId + "/status")
                        .cookie(userACookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(get("/api/progress/dashboard?date=2026-11-26").cookie(userACookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today.plannedCount").value(1))
                .andExpect(jsonPath("$.today.completedCount").value(1))
                .andExpect(jsonPath("$.today.completionRate").value(100.0));

        // 2. Edit past/existing task and verify record_history
        mockMvc.perform(put("/api/tasks/" + breakfastId)
                        .cookie(userACookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Eat breakfast - puri & chole",
                                  "category": "MEALS_PERSONAL",
                                  "taskDate": "2026-11-26",
                                  "startTime": "08:30",
                                  "endTime": "09:00",
                                  "priority": "HIGH",
                                  "status": "COMPLETED"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/tasks/" + breakfastId + "/history").cookie(userACookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.actionType == 'UPDATED')].changeSummary")
                        .value(org.hamcrest.Matchers.hasItem(containsString("Eat breakfast - puri"))));

        // 3. User B cannot read, edit, delete, or view history of User A's task (404)
        mockMvc.perform(get("/api/tasks?date=2026-11-26").cookie(userBCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/tasks/" + breakfastId + "/history").cookie(userBCookie))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/tasks/" + breakfastId + "/status")
                        .cookie(userBCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SKIPPED\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/tasks/" + breakfastId).cookie(userBCookie))
                .andExpect(status().isNotFound());

        // 4. Carry forward 2 unfinished tasks from yesterday
        Long y1 = createTaskAndReturnId(userACookie, "Revise Joins", yesterday, "SQL_MYSQL");
        Long y2 = createTaskAndReturnId(userACookie, "Practice Two Sum", yesterday, "CODING_PRACTICE");

        mockMvc.perform(post("/api/tasks/carry-forward")
                        .cookie(userACookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskIds\":[" + y1 + "," + y2 + "],\"targetDate\":\"2026-11-26\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].carriedFromTaskId").value(y1));

        // Yesterday's tasks remain intact with status RESCHEDULED
        mockMvc.perform(get("/api/tasks?date=2026-11-25").cookie(userACookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("RESCHEDULED"))
                .andExpect(jsonPath("$[1].status").value("RESCHEDULED"));
    }

    private Long createTaskAndReturnId(Cookie cookie, String title, LocalDate date, String category) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "title", title,
                "category", category,
                "taskDate", date.toString(),
                "status", "PENDING",
                "priority", "MEDIUM"
        ));
        MvcResult res = mockMvc.perform(post("/api/tasks")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode json = objectMapper.readTree(res.getResponse().getContentAsString());
        return json.get("id").asLong();
    }

    private Cookie registerAndLogin(String fullName, String phone, String email) throws Exception {
        String reg = objectMapper.writeValueAsString(Map.of(
                "fullName", fullName,
                "phone", phone,
                "email", email,
                "password", "Password123",
                "confirmPassword", "Password123"
        ));
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reg))
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
