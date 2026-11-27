package com.pathpilot.planner;

import com.pathpilot.coding.CodingActivity;
import com.pathpilot.coding.CodingActivityRepository;
import com.pathpilot.common.ApiException;
import com.pathpilot.job.JobApplicationRepository;
import com.pathpilot.planner.dto.GoalDtos.GoalCarryForwardRequest;
import com.pathpilot.planner.dto.GoalDtos.GoalDto;
import com.pathpilot.planner.dto.GoalDtos.GoalRequest;
import com.pathpilot.planner.dto.GoalDtos.WeeklyReviewDto;
import com.pathpilot.planner.dto.GoalDtos.WeeklyReviewRequest;
import com.pathpilot.project.ProjectMilestone;
import com.pathpilot.project.ProjectMilestoneRepository;
import com.pathpilot.user.UserProfile;
import com.pathpilot.user.UserProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class GoalService {

    private final GoalRepository goalRepository;
    private final WeeklyReviewRepository weeklyReviewRepository;
    private final TaskRepository taskRepository;
    private final CodingActivityRepository codingActivityRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final ProjectMilestoneRepository projectMilestoneRepository;
    private final RecordHistoryRepository recordHistoryRepository;
    private final UserProfileRepository userProfileRepository;

    public GoalService(
            GoalRepository goalRepository,
            WeeklyReviewRepository weeklyReviewRepository,
            TaskRepository taskRepository,
            CodingActivityRepository codingActivityRepository,
            JobApplicationRepository jobApplicationRepository,
            ProjectMilestoneRepository projectMilestoneRepository,
            RecordHistoryRepository recordHistoryRepository,
            UserProfileRepository userProfileRepository
    ) {
        this.goalRepository = goalRepository;
        this.weeklyReviewRepository = weeklyReviewRepository;
        this.taskRepository = taskRepository;
        this.codingActivityRepository = codingActivityRepository;
        this.jobApplicationRepository = jobApplicationRepository;
        this.projectMilestoneRepository = projectMilestoneRepository;
        this.recordHistoryRepository = recordHistoryRepository;
        this.userProfileRepository = userProfileRepository;
    }

    @Transactional(readOnly = true)
    public List<GoalDto> listGoals(Long userId, String scope, LocalDate periodStart) {
        String normalizedScope = parseScope(scope);
        LocalDate normalizedStart = normalizePeriodStart(userId, normalizedScope, periodStart);

        return goalRepository
                .findAllByUserIdAndPeriodTypeAndStartDateAndDeletedAtUtcIsNullOrderByIdAsc(
                        userId, normalizedScope, normalizedStart
                )
                .stream()
                .map(g -> toDto(userId, g))
                .toList();
    }

    @Transactional
    public GoalDto createGoal(Long userId, GoalRequest request) {
        String scope = parseScope(request.scope());
        GoalCategory category = parseCategory(request.category());
        LocalDate start = normalizePeriodStart(userId, scope, request.periodStart());
        LocalDate end = computePeriodEnd(scope, start);
        String status = parseStatus(request.status(), "PENDING");

        Goal goal = new Goal();
        goal.setUserId(userId);
        goal.setTitle(request.title().trim());
        goal.setCategory(category.name());
        goal.setPeriodType(scope);
        goal.setStartDate(start);
        goal.setTargetDate(end);
        goal.setTargetCount(request.targetCount());
        goal.setDoneCount(request.doneCount());
        goal.setDescription(trimToNull(request.description()));
        goal.setStatus(status);
        goal.setDataSource("MANUAL");

        Goal saved = goalRepository.save(goal);
        writeHistory(userId, "GOAL", saved.getId(), "CREATED", start,
                "Created " + scope + " goal \"" + saved.getTitle() + "\"");
        return toDto(userId, saved);
    }

    @Transactional
    public GoalDto updateGoal(Long userId, Long goalId, GoalRequest request) {
        Goal goal = requireOwnedGoal(userId, goalId);
        GoalCategory category = parseCategory(request.category());
        String oldSnapshot = "[title=\"" + goal.getTitle() + "\", target=" + goal.getTargetCount()
                + ", done=" + goal.getDoneCount() + ", status=" + goal.getStatus() + "]";

        goal.setTitle(request.title().trim());
        goal.setCategory(category.name());
        goal.setTargetCount(request.targetCount());
        goal.setDoneCount(request.doneCount());
        goal.setDescription(trimToNull(request.description()));
        goal.setStatus(parseStatus(request.status(), goal.getStatus()));

        Goal saved = goalRepository.save(goal);
        writeHistory(userId, "GOAL", saved.getId(), "UPDATED", saved.getStartDate(),
                "Old: " + oldSnapshot + " | New: [title=\"" + saved.getTitle() + "\", done=" + saved.getDoneCount() + "]");
        return toDto(userId, saved);
    }

    @Transactional
    public void softDeleteGoal(Long userId, Long goalId) {
        Goal goal = requireOwnedGoal(userId, goalId);
        goal.setDeletedAtUtc(Instant.now());
        goalRepository.save(goal);
        writeHistory(userId, "GOAL", goal.getId(), "DELETED", goal.getStartDate(),
                "Soft-deleted goal \"" + goal.getTitle() + "\"");
    }

    @Transactional
    public List<GoalDto> carryForwardGoals(Long userId, GoalCarryForwardRequest request) {
        List<GoalDto> created = new ArrayList<>();
        for (Long goalId : request.goalIds()) {
            Goal original = requireOwnedGoal(userId, goalId);
            String scope = original.getPeriodType();
            LocalDate targetStart = normalizePeriodStart(userId, scope, request.targetPeriodStart());
            if (targetStart.equals(original.getStartDate())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CARRY_FORWARD",
                        "Target period must differ from the original goal period.");
            }
            LocalDate targetEnd = computePeriodEnd(scope, targetStart);

            Goal copy = new Goal();
            copy.setUserId(userId);
            copy.setTitle(original.getTitle());
            copy.setCategory(original.getCategory());
            copy.setPeriodType(scope);
            copy.setStartDate(targetStart);
            copy.setTargetDate(targetEnd);
            copy.setTargetCount(original.getTargetCount());
            copy.setDoneCount(0);
            copy.setDescription(original.getDescription());
            copy.setStatus("PENDING");
            copy.setCarriedFromGoalId(original.getId());
            copy.setDataSource("MANUAL");
            Goal savedCopy = goalRepository.save(copy);

            original.setStatus("RESCHEDULED");
            goalRepository.save(original);

            writeHistory(userId, "GOAL", original.getId(), "RESCHEDULED", original.getStartDate(),
                    "Carried forward to " + targetStart + " (new goal #" + savedCopy.getId() + ")");
            created.add(toDto(userId, savedCopy));
        }
        return created;
    }

    @Transactional(readOnly = true)
    public WeeklyReviewDto getWeeklyReview(Long userId, LocalDate weekStart) {
        LocalDate monday = normalizePeriodStart(userId, "WEEK", weekStart);
        LocalDate sunday = monday.plusDays(6);
        return weeklyReviewRepository.findByUserIdAndWeekStartDate(userId, monday)
                .map(r -> new WeeklyReviewDto(
                        r.getWeekStartDate(),
                        r.getWeekEndDate(),
                        r.getWhatWentWell() != null ? r.getWhatWentWell() : "",
                        r.getCarryForwardNotes() != null ? r.getCarryForwardNotes() : "",
                        r.getUpdatedAtUtc()
                ))
                .orElse(new WeeklyReviewDto(monday, sunday, "", "", null));
    }

    @Transactional
    public WeeklyReviewDto saveWeeklyReview(Long userId, WeeklyReviewRequest request) {
        LocalDate monday = normalizePeriodStart(userId, "WEEK", request.weekStartDate());
        LocalDate sunday = monday.plusDays(6);

        WeeklyReview review = weeklyReviewRepository.findByUserIdAndWeekStartDate(userId, monday)
                .orElseGet(() -> {
                    WeeklyReview created = new WeeklyReview();
                    created.setUserId(userId);
                    created.setWeekStartDate(monday);
                    created.setWeekEndDate(sunday);
                    return created;
                });

        review.setWhatWentWell(trimToNull(request.whatWentWell()));
        review.setCarryForwardNotes(trimToNull(request.carryForwardNotes()));
        WeeklyReview saved = weeklyReviewRepository.save(review);

        return new WeeklyReviewDto(
                saved.getWeekStartDate(),
                saved.getWeekEndDate(),
                saved.getWhatWentWell() != null ? saved.getWhatWentWell() : "",
                saved.getCarryForwardNotes() != null ? saved.getCarryForwardNotes() : "",
                saved.getUpdatedAtUtc()
        );
    }

    private GoalDto toDto(Long userId, Goal goal) {
        GoalCategory category = parseCategorySafe(goal.getCategory());
        DoneCountResult computed = computeDoneCount(userId, goal, category);

        return new GoalDto(
                goal.getId(),
                goal.getTitle(),
                category.name(),
                category.getLabel(),
                category.getColorGroup(),
                goal.getPeriodType(),
                goal.getStartDate(),
                goal.getTargetDate(),
                goal.getTargetCount(),
                computed.doneCount(),
                computed.source(),
                computed.note(),
                goal.getDescription(),
                goal.getStatus(),
                goal.getCarriedFromGoalId()
        );
    }

    private DoneCountResult computeDoneCount(Long userId, Goal goal, GoalCategory category) {
        // 1. Linked tasks take precedence when present.
        List<Task> linkedTasks = taskRepository.findAllByUserIdAndGoalIdAndDeletedAtUtcIsNull(userId, goal.getId());
        if (!linkedTasks.isEmpty()) {
            int completed = (int) linkedTasks.stream()
                    .filter(t -> "COMPLETED".equalsIgnoreCase(t.getStatus()))
                    .count();
            return new DoneCountResult(completed, "CALCULATED", "Calculated from linked tasks");
        }

        LocalDate start = goal.getStartDate();
        LocalDate end = goal.getTargetDate();

        // 2. Period log entries by category where available.
        switch (category) {
            case CODING_PROBLEMS -> {
                List<CodingActivity> activities = codingActivityRepository
                        .findAllByUserIdAndActivityDateBetweenOrderByActivityDateDesc(userId, start, end);
                if (!activities.isEmpty()) {
                    int solved = activities.stream().mapToInt(CodingActivity::getProblemsSolved).sum();
                    return new DoneCountResult(solved, "CALCULATED", "Calculated from coding activity entries");
                }
            }
            case JOB_APPLICATIONS -> {
                var apps = jobApplicationRepository.findAllByUserIdAndAppliedDateBetween(userId, start, end);
                if (!apps.isEmpty()) {
                    return new DoneCountResult(apps.size(), "CALCULATED", "Calculated from job applications in period");
                }
            }
            case PROJECTS -> {
                List<ProjectMilestone> milestones = projectMilestoneRepository
                        .findAllByUserIdAndDeletedAtUtcIsNullAndDueDateBetweenOrderByDueDateAscIdAsc(userId, start, end);
                if (!milestones.isEmpty()) {
                    int done = (int) milestones.stream()
                            .filter(m -> "COMPLETED".equalsIgnoreCase(m.getStatus()))
                            .count();
                    return new DoneCountResult(done, "CALCULATED", "Calculated from completed milestones in period");
                }
            }
            default -> {
                // Other categories remain manual unless tasks are linked directly.
            }
        }

        return new DoneCountResult(goal.getDoneCount(), "MANUAL", "Tracked manually");
    }

    private Goal requireOwnedGoal(Long userId, Long goalId) {
        return goalRepository.findByIdAndUserIdAndDeletedAtUtcIsNull(goalId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "GOAL_NOT_FOUND", "Goal not found."));
    }

    private LocalDate normalizePeriodStart(Long userId, String scope, LocalDate supplied) {
        LocalDate base = supplied;
        if (base == null) {
            String tz = userProfileRepository.findByUserId(userId)
                    .map(UserProfile::getTimeZone)
                    .orElse(UserProfile.DEFAULT_TIME_ZONE);
            base = PeriodBoundaryHelper.localToday(Instant.now(), tz);
        }
        return "WEEK".equals(scope)
                ? PeriodBoundaryHelper.weekRangeContaining(base).start()
                : base.withDayOfMonth(1);
    }

    private LocalDate computePeriodEnd(String scope, LocalDate start) {
        return "WEEK".equals(scope)
                ? start.plusDays(6)
                : YearMonth.from(start).atEndOfMonth();
    }

    private String parseScope(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Scope is required.",
                    Map.of("scope", "Scope must be WEEK or MONTH."));
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (!"WEEK".equals(normalized) && !"MONTH".equals(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid scope.",
                    Map.of("scope", "Scope must be WEEK or MONTH."));
        }
        return normalized;
    }

    private GoalCategory parseCategory(String raw) {
        if (raw == null || raw.isBlank()) {
            return GoalCategory.OTHER;
        }
        try {
            return GoalCategory.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid goal category.",
                    Map.of("category", "Unsupported goal category: " + raw));
        }
    }

    private GoalCategory parseCategorySafe(String raw) {
        try {
            return GoalCategory.valueOf(raw);
        } catch (Exception ex) {
            return GoalCategory.OTHER;
        }
    }

    private String parseStatus(String raw, String fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void writeHistory(Long userId, String type, Long id, String action, LocalDate date, String summary) {
        RecordHistory h = new RecordHistory();
        h.setUserId(userId);
        h.setEntityType(type);
        h.setEntityId(id);
        h.setActionType(action);
        h.setActorSource("MANUAL");
        h.setRecordDate(date);
        h.setChangeSummary(summary);
        recordHistoryRepository.save(h);
    }

    private record DoneCountResult(int doneCount, String source, String note) {
    }
}
