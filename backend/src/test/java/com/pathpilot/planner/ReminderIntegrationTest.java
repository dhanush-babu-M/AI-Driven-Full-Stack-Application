package com.pathpilot.planner;

import com.pathpilot.coding.CodingActivityRepository;
import com.pathpilot.github.ConnectedAccountRepository;
import com.pathpilot.github.GithubEventRepository;
import com.pathpilot.job.JobApplicationRepository;
import com.pathpilot.job.JobFollowupRepository;
import com.pathpilot.job.JobStatusHistoryRepository;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
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
class ReminderIntegrationTest {

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
    @MockitoBean private NotificationPreferencesRepository preferencesRepository;
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
    private final Map<Long, Task> tasks = new ConcurrentHashMap<>();
    private final Map<Long, NotificationPreferences> prefsByUser = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        authRateLimitFilter.clearAll();
        users.clear();
        profiles.clear();
        tasks.clear();
        prefsByUser.clear();

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

        when(preferencesRepository.findByUserId(anyLong())).thenAnswer(inv ->
                Optional.ofNullable(prefsByUser.get(inv.getArgument(0, Long.class))));
        when(preferencesRepository.save(any(NotificationPreferences.class))).thenAnswer(inv -> {
            NotificationPreferences p = inv.getArgument(0);
            if (p.getId() == null) p.setId(seq.getAndIncrement());
            prefsByUser.put(p.getUserId(), p);
            return p;
        });

        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> {
            Task t = inv.getArgument(0);
            if (t.getId() == null) t.setId(seq.getAndIncrement());
            tasks.put(t.getId(), t);
            return t;
        });
        when(taskRepository.findByIdAndUserIdAndDeletedAtUtcIsNull(anyLong(), anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0, Long.class);
            Long uid = inv.getArgument(1, Long.class);
            Task t = tasks.get(id);
            return (t != null && t.getUserId().equals(uid) && t.getDeletedAtUtc() == null)
                    ? Optional.of(t) : Optional.empty();
        });
        when(taskRepository.findAllByUserIdAndReminderEnabledTrueAndStatusInAndDeletedAtUtcIsNullOrderByReminderAtUtcAsc(
                anyLong(), anyCollection()))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0, Long.class);
                    @SuppressWarnings("unchecked")
                    Collection<String> statuses = inv.getArgument(1, Collection.class);
                    return tasks.values().stream()
                            .filter(t -> t.getUserId().equals(uid) && t.isReminderEnabled()
                                    && t.getDeletedAtUtc() == null && statuses.contains(t.getStatus()))
                            .toList();
                });
        when(taskRepository.findAllByUserIdAndReminderEnabledTrueAndReminderAtUtcBetweenAndStatusInAndDeletedAtUtcIsNullOrderByReminderAtUtcAsc(
                anyLong(), any(Instant.class), any(Instant.class), anyCollection()))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0, Long.class);
                    Instant from = inv.getArgument(1, Instant.class);
                    Instant to = inv.getArgument(2, Instant.class);
                    @SuppressWarnings("unchecked")
                    Collection<String> statuses = inv.getArgument(3, Collection.class);
                    return tasks.values().stream()
                            .filter(t -> t.getUserId().equals(uid) && t.isReminderEnabled()
                                    && t.getReminderAtUtc() != null
                                    && !t.getReminderAtUtc().isBefore(from) && !t.getReminderAtUtc().isAfter(to)
                                    && t.getDeletedAtUtc() == null && statuses.contains(t.getStatus()))
                            .toList();
                });

        when(dailyLogRepository.findByUserIdAndLogDate(anyLong(), any(LocalDate.class))).thenReturn(Optional.empty());
        when(dailyLogRepository.save(any(DailyLog.class))).thenAnswer(inv -> inv.getArgument(0));
        when(recordHistoryRepository.save(any(RecordHistory.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void preferencesAndUpcomingReminders_workAndRejectNoStartTime() throws Exception {
        Cookie cookie = registerAndLogin("Neha Bell", "9876511111", "neha@example.com");

        // Update notification preferences: 1 min lead
        mockMvc.perform(put("/api/notifications/preferences")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "browserEnabled": true,
                                  "leadMinutes": 1,
                                  "quietHoursStart": "23:00",
                                  "quietHoursEnd": "06:00"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.browserEnabled").value(true))
                .andExpect(jsonPath("$.leadMinutes").value(1));

        // Create breakfast task 2 minutes from now in Asia/Kolkata
        var targetZdt = Instant.now().plusSeconds(120).atZone(ZoneId.of("Asia/Kolkata"));
        String taskDate = targetZdt.toLocalDate().toString();
        String startTime = targetZdt.toLocalTime().withSecond(0).withNano(0).toString();
        String endTime = targetZdt.plusMinutes(30).toLocalTime().withSecond(0).withNano(0).toString();
        if (LocalTime.parse(endTime).isBefore(LocalTime.parse(startTime))) {
            // Avoid midnight wrap inside test assertion
            taskDate = targetZdt.toLocalDate().toString();
            startTime = "10:00";
            endTime = "10:30";
        }

        MvcResult created = mockMvc.perform(post("/api/tasks")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Eat breakfast - puri",
                                "category", "MEALS_PERSONAL",
                                "taskDate", taskDate,
                                "startTime", startTime,
                                "endTime", endTime,
                                "priority", "MEDIUM",
                                "status", "PLANNED",
                                "reminderEnabled", true
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reminderEnabled").value(true))
                .andReturn();

        Long taskId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/reminders/upcoming?hours=24").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].taskId").value(taskId))
                .andExpect(jsonPath("$[0].title").value("Eat breakfast - puri"));

        // Task without start time cannot enable a reminder
        MvcResult noTime = mockMvc.perform(post("/api/tasks")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Flexible reading",
                                "category", "JAVA_LEARNING",
                                "taskDate", taskDate,
                                "status", "PLANNED"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        Long noTimeId = objectMapper.readTree(noTime.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/api/tasks/" + noTimeId + "/reminder")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NO_START_TIME"));
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
