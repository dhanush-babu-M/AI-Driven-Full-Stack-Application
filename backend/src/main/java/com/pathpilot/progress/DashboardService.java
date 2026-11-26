package com.pathpilot.progress;

import com.pathpilot.coding.CodingActivity;
import com.pathpilot.coding.CodingActivityRepository;
import com.pathpilot.common.ApiException;
import com.pathpilot.github.GithubEvent;
import com.pathpilot.github.GithubEventRepository;
import com.pathpilot.job.JobApplication;
import com.pathpilot.job.JobApplicationRepository;
import com.pathpilot.planner.Task;
import com.pathpilot.planner.TaskCategory;
import com.pathpilot.planner.TaskRepository;
import com.pathpilot.progress.dto.DashboardResponse;
import com.pathpilot.progress.dto.DashboardResponse.CategoryRatesDto;
import com.pathpilot.progress.dto.DashboardResponse.CodingStreakSection;
import com.pathpilot.progress.dto.DashboardResponse.CurrentProjectDto;
import com.pathpilot.progress.dto.DashboardResponse.FocusTaskDto;
import com.pathpilot.progress.dto.DashboardResponse.GithubDayDto;
import com.pathpilot.progress.dto.DashboardResponse.GithubWeekDto;
import com.pathpilot.progress.dto.DashboardResponse.MonthlySection;
import com.pathpilot.progress.dto.DashboardResponse.QuickLinksDto;
import com.pathpilot.progress.dto.DashboardResponse.RecentApplicationDto;
import com.pathpilot.progress.dto.DashboardResponse.RecommendationDto;
import com.pathpilot.progress.dto.DashboardResponse.TodaySection;
import com.pathpilot.project.Project;
import com.pathpilot.project.ProjectMilestone;
import com.pathpilot.project.ProjectMilestoneRepository;
import com.pathpilot.project.ProjectRepository;
import com.pathpilot.user.UserProfile;
import com.pathpilot.user.UserProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final int GITHUB_WEEKS = 12;
    private static final int CODING_LOOKBACK_DAYS = 90;
    private static final int NO_CODING_RULE_DAYS = 2;

    private final UserProfileRepository userProfileRepository;
    private final TaskRepository taskRepository;
    private final CodingActivityRepository codingActivityRepository;
    private final GithubEventRepository githubEventRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMilestoneRepository projectMilestoneRepository;
    private final JobApplicationRepository jobApplicationRepository;

    public DashboardService(
            UserProfileRepository userProfileRepository,
            TaskRepository taskRepository,
            CodingActivityRepository codingActivityRepository,
            GithubEventRepository githubEventRepository,
            ProjectRepository projectRepository,
            ProjectMilestoneRepository projectMilestoneRepository,
            JobApplicationRepository jobApplicationRepository
    ) {
        this.userProfileRepository = userProfileRepository;
        this.taskRepository = taskRepository;
        this.codingActivityRepository = codingActivityRepository;
        this.githubEventRepository = githubEventRepository;
        this.projectRepository = projectRepository;
        this.projectMilestoneRepository = projectMilestoneRepository;
        this.jobApplicationRepository = jobApplicationRepository;
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(Long userId, LocalDate requestedDate) {
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROFILE_MISSING", "User profile not found."));

        String timeZone = (profile.getTimeZone() == null || profile.getTimeZone().isBlank())
                ? UserProfile.DEFAULT_TIME_ZONE
                : profile.getTimeZone();
        ZoneId zoneId = safeZoneId(timeZone);
        LocalDate targetDate = requestedDate != null ? requestedDate : LocalDate.now(zoneId);

        String greetingName = extractFirstName(profile.getFullName());
        String targetRole = (profile.getTargetRole() == null || profile.getTargetRole().isBlank())
                ? "Fresher"
                : profile.getTargetRole();

        // 1. Today's tasks (filtered strictly by userId, excluding soft-deleted)
        List<Task> todayTasks = taskRepository
                .findAllByUserIdAndTaskDateAndDeletedAtUtcIsNullOrderByPriorityAscStartTimeAscIdAsc(userId, targetDate);
        ProgressCalculator.TaskSummary todaySummary = ProgressCalculator.summarizeTasks(
                todayTasks.stream().map(Task::getStatus).toList()
        );
        CategoryRatesDto categoryRates = computeCategoryRates(todayTasks);
        List<FocusTaskDto> focusTasks = todayTasks.stream()
                .filter(t -> !ProgressCalculator.STATUS_RESCHEDULED.equalsIgnoreCase(t.getStatus()))
                .limit(4)
                .map(t -> new FocusTaskDto(
                        t.getId(),
                        t.getTitle(),
                        normalizeCategory(t.getCategory()),
                        t.getStatus(),
                        t.getPriority(),
                        t.getDurationMinutes(),
                        t.getDataSource()
                ))
                .toList();

        TodaySection todaySection = new TodaySection(
                todaySummary.plannedCount(),
                todaySummary.completedCount(),
                todaySummary.pendingCount(),
                todaySummary.skippedCount(),
                todaySummary.rescheduledCount(),
                todaySummary.completionRate(),
                categoryRates,
                focusTasks
        );

        // 2. Monthly tasks (filtered strictly by userId, excluding soft-deleted)
        LocalDate monthStart = targetDate.withDayOfMonth(1);
        LocalDate monthEnd = targetDate.withDayOfMonth(targetDate.lengthOfMonth());
        List<Task> monthTasks = taskRepository
                .findAllByUserIdAndTaskDateBetweenAndDeletedAtUtcIsNull(userId, monthStart, monthEnd);
        ProgressCalculator.TaskSummary monthSummary = ProgressCalculator.summarizeTasks(
                monthTasks.stream().map(Task::getStatus).toList()
        );
        MonthlySection monthlySection = new MonthlySection(
                targetDate.format(MONTH_FORMAT),
                monthSummary.plannedCount(),
                monthSummary.completedCount(),
                monthSummary.completionRate()
        );

        // 3. Honest Coding Streak
        LocalDate codingStart = targetDate.minusDays(CODING_LOOKBACK_DAYS);
        List<CodingActivity> codingActivities = codingActivityRepository
                .findAllByUserIdAndActivityDateBetweenOrderByActivityDateDesc(userId, codingStart, targetDate);
        Set<LocalDate> codingDates = codingActivities.stream()
                .map(CodingActivity::getActivityDate)
                .collect(Collectors.toSet());
        int streakDays = ProgressCalculator.calculateCodingStreak(codingDates, targetDate);
        boolean recordedToday = codingDates.contains(targetDate);
        String streakSource = codingActivities.stream()
                .anyMatch(a -> "DEMO".equalsIgnoreCase(a.getDataSource()))
                ? "DEMO"
                : "CALCULATED";
        CodingStreakSection codingStreak = new CodingStreakSection(
                streakDays,
                recordedToday,
                buildStreakMessage(streakDays, recordedToday),
                streakSource
        );

        // 4. GitHub Mini Heatmap (Mon-Sun rows, last 12 weeks)
        LocalDate endOfCurrentWeek = targetDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        LocalDate gridStart = endOfCurrentWeek.minusWeeks(GITHUB_WEEKS).plusDays(1); // Monday
        List<GithubEvent> githubEvents = githubEventRepository.findAllByUserIdAndEventDateBetween(
                userId, gridStart, targetDate
        );
        boolean hasGithubActivity = !githubEvents.isEmpty();
        List<GithubWeekDto> githubWeeks = buildGithubWeeks(gridStart, targetDate, githubEvents);

        // 5. Current Project Progress
        Optional<Project> projectOpt = projectRepository.findFirstByUserIdOrderByUpdatedAtUtcDescIdDesc(userId);
        CurrentProjectDto currentProject = projectOpt.map(project -> {
            List<ProjectMilestone> milestones = projectMilestoneRepository
                    .findAllByUserIdAndProjectIdOrderBySortOrderAscIdAsc(userId, project.getId());
            List<String> milestoneStatuses = milestones.stream().map(ProjectMilestone::getStatus).toList();
            int completedMilestones = (int) milestoneStatuses.stream()
                    .filter(ProgressCalculator.STATUS_COMPLETED::equalsIgnoreCase)
                    .count();
            Double projectProgress = ProgressCalculator.calculateProjectProgress(milestoneStatuses);
            return new CurrentProjectDto(
                    project.getId(),
                    project.getTitle(),
                    project.getStatus(),
                    completedMilestones,
                    milestones.size(),
                    projectProgress,
                    project.getDataSource()
            );
        }).orElse(null);

        // 6. Recent Job Applications (last 3)
        List<JobApplication> applications = jobApplicationRepository.findTop3ByUserIdOrderByAppliedDateDescIdDesc(userId);
        List<RecentApplicationDto> recentApplications = applications.stream()
                .map(app -> new RecentApplicationDto(
                        app.getId(),
                        app.getCompanyName(),
                        app.getRoleTitle(),
                        app.getStatus(),
                        app.getAppliedDate(),
                        app.getLocation(),
                        app.getDataSource()
                ))
                .toList();

        // 7. Quick Links from user_profiles
        QuickLinksDto links = new QuickLinksDto(
                profile.getPortfolioUrl(),
                profile.getGithubUrl(),
                profile.getLinkedinUrl(),
                profile.getCodingProfileUrl()
        );

        // 8. Rule-based Recommendation (no AI calls yet)
        RecommendationDto recommendation = buildRuleRecommendation(todaySummary, codingActivities, targetDate);

        boolean isDemo = isDemoData(todayTasks, monthTasks, codingActivities, githubEvents, currentProject, recentApplications);

        return new DashboardResponse(
                targetDate,
                timeZone,
                greetingName,
                targetRole,
                isDemo,
                todaySection,
                monthlySection,
                codingStreak,
                hasGithubActivity,
                githubWeeks,
                currentProject,
                recentApplications,
                links,
                recommendation
        );
    }

    private CategoryRatesDto computeCategoryRates(List<Task> tasks) {
        return new CategoryRatesDto(
                rateForCategory(tasks, "LEARNING"),
                rateForCategory(tasks, "PRACTICE"),
                rateForCategory(tasks, "PROJECTS"),
                rateForCategory(tasks, "APPLICATIONS")
        );
    }

    private Double rateForCategory(List<Task> tasks, String targetBucket) {
        List<String> statuses = tasks.stream()
                .filter(t -> targetBucket.equalsIgnoreCase(toDashboardBucket(t.getCategory())))
                .map(Task::getStatus)
                .toList();
        return ProgressCalculator.calculateTaskCompletionRate(statuses);
    }

    private String toDashboardBucket(String category) {
        String normalized = normalizeCategory(category);
        return switch (normalized) {
            case "LEARNING", "PRACTICE", "PROJECTS", "APPLICATIONS" -> normalized;
            default -> {
                try {
                    yield TaskCategory.valueOf(normalized).getDashboardBucket();
                } catch (IllegalArgumentException ex) {
                    yield "LEARNING";
                }
            }
        };
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return "JAVA_LEARNING";
        }
        return category.trim().toUpperCase(Locale.ROOT);
    }

    private List<GithubWeekDto> buildGithubWeeks(
            LocalDate gridStartMonday,
            LocalDate targetDate,
            List<GithubEvent> events
    ) {
        Map<LocalDate, Integer> countsByDate = new HashMap<>();
        for (GithubEvent event : events) {
            countsByDate.merge(event.getEventDate(), 1, Integer::sum);
        }

        List<GithubWeekDto> weeks = new ArrayList<>(GITHUB_WEEKS);
        for (int w = 0; w < GITHUB_WEEKS; w++) {
            List<GithubDayDto> days = new ArrayList<>(7);
            for (int d = 0; d < 7; d++) {
                LocalDate cellDate = gridStartMonday.plusWeeks(w).plusDays(d);
                if (cellDate.isAfter(targetDate)) {
                    days.add(new GithubDayDto(cellDate, 0, -1));
                } else {
                    int count = countsByDate.getOrDefault(cellDate, 0);
                    days.add(new GithubDayDto(cellDate, count, toHeatmapLevel(count)));
                }
            }
            weeks.add(new GithubWeekDto(days));
        }
        return weeks;
    }

    private int toHeatmapLevel(int count) {
        if (count <= 0) return 0;
        if (count == 1) return 1;
        if (count == 2) return 2;
        if (count <= 4) return 3;
        return 4;
    }

    private RecommendationDto buildRuleRecommendation(
            ProgressCalculator.TaskSummary todaySummary,
            List<CodingActivity> codingActivities,
            LocalDate targetDate
    ) {
        if (todaySummary.plannedCount() == 0) {
            return new RecommendationDto(
                    "No tasks planned for today yet. Add 2–3 focused tasks to build daily momentum.",
                    "RULE",
                    "Plan today",
                    "/app/planner/daily"
            );
        }

        LocalDate latestCoding = codingActivities.isEmpty() ? null : codingActivities.get(0).getActivityDate();
        boolean codingGap = latestCoding == null || ChronoUnit.DAYS.between(latestCoding, targetDate) >= NO_CODING_RULE_DAYS;
        if (codingGap) {
            return new RecommendationDto(
                    "No coding practice recorded in the last " + NO_CODING_RULE_DAYS + " days. Try solving and logging one problem today.",
                    "RULE",
                    "Log coding",
                    "/app/coding"
            );
        }

        if (todaySummary.pendingCount() > 0) {
            return new RecommendationDto(
                    "You have " + todaySummary.pendingCount() + " pending task(s) today. Focus on your top priority item next.",
                    "RULE",
                    "Open planner",
                    "/app/planner/daily"
            );
        }

        return new RecommendationDto(
                "All planned tasks for today are handled! Check your active project milestones or log what you learned.",
                "RULE",
                "View projects",
                "/app/projects"
        );
    }

    private String buildStreakMessage(int streakDays, boolean recordedToday) {
        if (streakDays == 0) {
            return "No coding entries recorded yet. Log practice to start an honest streak.";
        }
        if (!recordedToday) {
            return streakDays + " day(s) through yesterday — today is unrecorded, not failed.";
        }
        return streakDays + " consecutive day(s) with recorded coding practice.";
    }

    private boolean isDemoData(
            List<Task> todayTasks,
            List<Task> monthTasks,
            List<CodingActivity> coding,
            List<GithubEvent> github,
            CurrentProjectDto project,
            List<RecentApplicationDto> apps
    ) {
        return todayTasks.stream().anyMatch(t -> "DEMO".equalsIgnoreCase(t.getDataSource()))
                || monthTasks.stream().anyMatch(t -> "DEMO".equalsIgnoreCase(t.getDataSource()))
                || coding.stream().anyMatch(c -> "DEMO".equalsIgnoreCase(c.getDataSource()))
                || github.stream().anyMatch(g -> "DEMO".equalsIgnoreCase(g.getDataSource()))
                || (project != null && "DEMO".equalsIgnoreCase(project.dataSource()))
                || apps.stream().anyMatch(a -> "DEMO".equalsIgnoreCase(a.dataSource()));
    }

    private ZoneId safeZoneId(String timeZone) {
        try {
            return ZoneId.of(timeZone);
        } catch (Exception ex) {
            return ZoneId.of(UserProfile.DEFAULT_TIME_ZONE);
        }
    }

    private String extractFirstName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "there";
        }
        String cleaned = fullName.replace("(DEMO)", "").trim();
        if (cleaned.isEmpty()) {
            return "there";
        }
        int spaceIndex = cleaned.indexOf(' ');
        return spaceIndex > 0 ? cleaned.substring(0, spaceIndex) : cleaned;
    }
}
