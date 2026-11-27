package com.pathpilot.progress;

import com.pathpilot.planner.PeriodBoundaryHelper;
import com.pathpilot.planner.Task;
import com.pathpilot.planner.TaskCategory;
import com.pathpilot.planner.TaskRepository;
import com.pathpilot.planner.TaskService;
import com.pathpilot.planner.dto.MonthDaySummaryDto;
import com.pathpilot.planner.dto.TaskDto;
import com.pathpilot.progress.dto.DashboardResponse.CategoryRatesDto;
import com.pathpilot.progress.dto.PeriodProgressDtos.CategoryChangeDto;
import com.pathpilot.progress.dto.PeriodProgressDtos.DayTaskCountDto;
import com.pathpilot.progress.dto.PeriodProgressDtos.MonthMilestoneDto;
import com.pathpilot.progress.dto.PeriodProgressDtos.MonthWorklogDto;
import com.pathpilot.progress.dto.PeriodProgressDtos.MonthlyProgressResponse;
import com.pathpilot.progress.dto.PeriodProgressDtos.WeeklyProgressResponse;
import com.pathpilot.project.Project;
import com.pathpilot.project.ProjectMilestone;
import com.pathpilot.project.ProjectMilestoneRepository;
import com.pathpilot.project.ProjectRepository;
import com.pathpilot.project.ProjectWorklog;
import com.pathpilot.project.ProjectWorklogRepository;
import com.pathpilot.user.UserProfile;
import com.pathpilot.user.UserProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class PeriodProgressService {

    private final UserProfileRepository userProfileRepository;
    private final TaskRepository taskRepository;
    private final TaskService taskService;
    private final ProjectMilestoneRepository projectMilestoneRepository;
    private final ProjectWorklogRepository projectWorklogRepository;
    private final ProjectRepository projectRepository;

    public PeriodProgressService(
            UserProfileRepository userProfileRepository,
            TaskRepository taskRepository,
            TaskService taskService,
            ProjectMilestoneRepository projectMilestoneRepository,
            ProjectWorklogRepository projectWorklogRepository,
            ProjectRepository projectRepository
    ) {
        this.userProfileRepository = userProfileRepository;
        this.taskRepository = taskRepository;
        this.taskService = taskService;
        this.projectMilestoneRepository = projectMilestoneRepository;
        this.projectWorklogRepository = projectWorklogRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional(readOnly = true)
    public WeeklyProgressResponse getWeeklyProgress(Long userId, LocalDate startParam) {
        String timeZone = resolveTimeZone(userId);
        LocalDate anchor = startParam != null
                ? startParam
                : PeriodBoundaryHelper.localToday(Instant.now(), timeZone);
        PeriodBoundaryHelper.DateRange week = PeriodBoundaryHelper.weekRangeContaining(anchor);

        List<Task> tasks = taskRepository.findAllByUserIdAndTaskDateBetweenAndDeletedAtUtcIsNull(
                userId, week.start(), week.end()
        );
        ProgressCalculator.TaskSummary summary = ProgressCalculator.summarizeTasks(
                tasks.stream().map(Task::getStatus).toList()
        );
        CategoryRatesDto categoryRates = computeCategoryRates(tasks);

        List<DayTaskCountDto> days = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            LocalDate day = week.start().plusDays(i);
            List<Task> dayTasks = tasks.stream().filter(t -> day.equals(t.getTaskDate())).toList();
            ProgressCalculator.TaskSummary ds = ProgressCalculator.summarizeTasks(
                    dayTasks.stream().map(Task::getStatus).toList()
            );
            days.add(new DayTaskCountDto(
                    day,
                    ds.completedCount(),
                    ds.pendingCount(),
                    ds.skippedCount(),
                    ds.rescheduledCount(),
                    !dayTasks.isEmpty()
            ));
        }

        List<TaskDto> taskDtos = tasks.stream().map(taskService::toDto).toList();

        return new WeeklyProgressResponse(
                week.start(),
                week.end(),
                timeZone,
                summary.plannedCount(),
                summary.completedCount(),
                summary.pendingCount(),
                summary.skippedCount(),
                summary.rescheduledCount(),
                summary.completionRate(),
                categoryRates,
                days,
                taskDtos
        );
    }

    @Transactional(readOnly = true)
    public MonthlyProgressResponse getMonthlyProgress(Long userId, String monthParam) {
        String timeZone = resolveTimeZone(userId);
        YearMonth month = (monthParam != null && !monthParam.isBlank())
                ? YearMonth.parse(monthParam)
                : YearMonth.from(PeriodBoundaryHelper.localToday(Instant.now(), timeZone));

        PeriodBoundaryHelper.DateRange range = PeriodBoundaryHelper.monthRange(month);
        List<Task> tasks = taskRepository.findAllByUserIdAndTaskDateBetweenAndDeletedAtUtcIsNull(
                userId, range.start(), range.end()
        );
        ProgressCalculator.TaskSummary summary = ProgressCalculator.summarizeTasks(
                tasks.stream().map(Task::getStatus).toList()
        );
        CategoryRatesDto categoryRates = computeCategoryRates(tasks);

        // Previous month comparison ONLY when previous month has planned data.
        YearMonth prevMonth = month.minusMonths(1);
        PeriodBoundaryHelper.DateRange prevRange = PeriodBoundaryHelper.monthRange(prevMonth);
        List<Task> prevTasks = taskRepository.findAllByUserIdAndTaskDateBetweenAndDeletedAtUtcIsNull(
                userId, prevRange.start(), prevRange.end()
        );
        ProgressCalculator.TaskSummary prevSummary = ProgressCalculator.summarizeTasks(
                prevTasks.stream().map(Task::getStatus).toList()
        );
        CategoryRatesDto prevRates = computeCategoryRates(prevTasks);

        Double overallChange = diffIfBothPresent(summary.completionRate(), prevSummary.completionRate());
        CategoryChangeDto categoryChanges = new CategoryChangeDto(
                diffIfBothPresent(categoryRates.learning(), prevRates.learning()),
                diffIfBothPresent(categoryRates.practice(), prevRates.practice()),
                diffIfBothPresent(categoryRates.projects(), prevRates.projects()),
                diffIfBothPresent(categoryRates.applications(), prevRates.applications())
        );

        List<MonthDaySummaryDto> daySummaries = taskService.getMonthSummary(userId, month);
        List<ProjectMilestone> milestones = projectMilestoneRepository
                .findAllByUserIdAndDeletedAtUtcIsNullAndDueDateBetweenOrderByDueDateAscIdAsc(
                        userId, range.start(), range.end()
                );
        List<MonthMilestoneDto> milestoneDtos = milestones.stream()
                .map(m -> new MonthMilestoneDto(
                        m.getId(),
                        m.getProjectId(),
                        m.getTitle(),
                        m.getDueDate(),
                        m.getStatus(),
                        m.getDataSource()
                ))
                .toList();

        List<ProjectWorklog> worklogs = projectWorklogRepository
                .findAllByUserIdAndDeletedAtUtcIsNullAndWorkDateBetweenOrderByWorkDateDescIdDesc(
                        userId, range.start(), range.end()
                );
        java.util.Map<Long, String> projectTitles = projectRepository
                .findAllByUserIdAndDeletedAtUtcIsNullOrderByUpdatedAtUtcDescIdDesc(userId)
                .stream()
                .collect(java.util.stream.Collectors.toMap(Project::getId, Project::getTitle, (a, b) -> a));
        List<MonthWorklogDto> worklogDtos = worklogs.stream()
                .map(w -> new MonthWorklogDto(
                        w.getId(),
                        w.getProjectId(),
                        projectTitles.getOrDefault(w.getProjectId(), "Project #" + w.getProjectId()),
                        w.getWorkDate(),
                        w.getMinutesSpent(),
                        w.getSummary(),
                        w.getDataSource()
                ))
                .toList();

        return new MonthlyProgressResponse(
                month.toString(),
                range.start(),
                range.end(),
                timeZone,
                summary.plannedCount(),
                summary.completedCount(),
                summary.pendingCount(),
                summary.skippedCount(),
                summary.rescheduledCount(),
                summary.completionRate(),
                overallChange,
                categoryRates,
                categoryChanges,
                daySummaries,
                milestoneDtos,
                worklogDtos
        );
    }

    private CategoryRatesDto computeCategoryRates(List<Task> tasks) {
        return new CategoryRatesDto(
                rateForBucket(tasks, "LEARNING"),
                rateForBucket(tasks, "PRACTICE"),
                rateForBucket(tasks, "PROJECTS"),
                rateForBucket(tasks, "APPLICATIONS")
        );
    }

    private Double rateForBucket(List<Task> tasks, String targetBucket) {
        List<String> statuses = tasks.stream()
                .filter(t -> targetBucket.equalsIgnoreCase(toDashboardBucket(t.getCategory())))
                .map(Task::getStatus)
                .toList();
        return ProgressCalculator.calculateTaskCompletionRate(statuses);
    }

    private String toDashboardBucket(String category) {
        if (category == null || category.isBlank()) return "LEARNING";
        String normalized = category.trim().toUpperCase(Locale.ROOT);
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

    private Double diffIfBothPresent(Double current, Double previous) {
        if (current == null || previous == null) {
            return null;
        }
        return Math.round((current - previous) * 10.0) / 10.0;
    }

    private String resolveTimeZone(Long userId) {
        return userProfileRepository.findByUserId(userId)
                .map(UserProfile::getTimeZone)
                .filter(tz -> tz != null && !tz.isBlank())
                .orElse(UserProfile.DEFAULT_TIME_ZONE);
    }
}
