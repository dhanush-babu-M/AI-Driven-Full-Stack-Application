package com.pathpilot.planner;

import com.pathpilot.common.ApiException;
import com.pathpilot.planner.dto.CarryForwardRequest;
import com.pathpilot.planner.dto.MonthDaySummaryDto;
import com.pathpilot.planner.dto.RecordHistoryDto;
import com.pathpilot.planner.dto.TaskDto;
import com.pathpilot.planner.dto.TaskReminderRequest;
import com.pathpilot.planner.dto.TaskRequest;
import com.pathpilot.planner.dto.TaskStatusRequest;
import com.pathpilot.user.UserProfile;
import com.pathpilot.user.UserProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class TaskService {

    private static final List<String> UNFINISHED_STATUSES = List.of("PLANNED", "PENDING");

    private final TaskRepository taskRepository;
    private final DailyLogRepository dailyLogRepository;
    private final RecordHistoryRepository recordHistoryRepository;
    private final NotificationPreferencesRepository notificationPreferencesRepository;
    private final UserProfileRepository userProfileRepository;

    public TaskService(
            TaskRepository taskRepository,
            DailyLogRepository dailyLogRepository,
            RecordHistoryRepository recordHistoryRepository,
            NotificationPreferencesRepository notificationPreferencesRepository,
            UserProfileRepository userProfileRepository
    ) {
        this.taskRepository = taskRepository;
        this.dailyLogRepository = dailyLogRepository;
        this.recordHistoryRepository = recordHistoryRepository;
        this.notificationPreferencesRepository = notificationPreferencesRepository;
        this.userProfileRepository = userProfileRepository;
    }

    @Transactional(readOnly = true)
    public List<TaskDto> listByDate(Long userId, LocalDate date) {
        return taskRepository
                .findAllByUserIdAndTaskDateAndDeletedAtUtcIsNullOrderByPriorityAscStartTimeAscIdAsc(userId, date)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaskDto> listUnfinishedBefore(Long userId, LocalDate beforeDate) {
        return taskRepository
                .findAllByUserIdAndTaskDateBeforeAndStatusInAndDeletedAtUtcIsNullOrderByTaskDateDescPriorityAscIdAsc(
                        userId, beforeDate, UNFINISHED_STATUSES
                )
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MonthDaySummaryDto> getMonthSummary(Long userId, YearMonth month) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();

        List<Task> tasks = taskRepository.findAllByUserIdAndTaskDateBetweenAndDeletedAtUtcIsNull(userId, start, end);
        List<DailyLog> logs = dailyLogRepository.findAllByUserIdAndLogDateBetween(userId, start, end);

        Map<LocalDate, String> logStatusByDate = new LinkedHashMap<>();
        for (DailyLog log : logs) {
            logStatusByDate.put(log.getLogDate(), log.getDayStatus());
        }

        Map<LocalDate, int[]> countsByDate = new LinkedHashMap<>();
        for (Task task : tasks) {
            if (TaskStatus.RESCHEDULED.name().equalsIgnoreCase(task.getStatus())) {
                continue;
            }
            int[] counts = countsByDate.computeIfAbsent(task.getTaskDate(), ignored -> new int[3]);
            String st = task.getStatus() == null ? "PLANNED" : task.getStatus().toUpperCase(Locale.ROOT);
            if (TaskStatus.COMPLETED.name().equals(st)) {
                counts[0]++;
            } else if (TaskStatus.SKIPPED.name().equals(st)) {
                counts[2]++;
            } else {
                counts[1]++;
            }
        }

        List<MonthDaySummaryDto> summaries = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            int[] counts = countsByDate.get(d);
            String dayStatus = logStatusByDate.get(d);
            if (counts != null || dayStatus != null) {
                summaries.add(new MonthDaySummaryDto(
                        d,
                        counts != null ? counts[0] : 0,
                        counts != null ? counts[1] : 0,
                        counts != null ? counts[2] : 0,
                        dayStatus != null ? dayStatus : "RECORDED"
                ));
            }
        }
        return summaries;
    }

    @Transactional
    public TaskDto create(Long userId, TaskRequest request) {
        TaskCategory category = parseCategory(request.category());
        TaskPriority priority = parsePriority(request.priority());
        TaskStatus status = parseStatus(request.status(), TaskStatus.PLANNED);
        validateTimeRange(request.startTime(), request.endTime());

        Task task = new Task();
        task.setUserId(userId);
        task.setTitle(request.title().trim());
        task.setDescription(trimToNull(request.description()));
        task.setTaskDate(request.taskDate());
        task.setStartTime(request.startTime());
        task.setEndTime(request.endTime());
        task.setCategory(category.name());
        task.setPriority(priority.getRank());
        task.setStatus(status.name());
        task.setDurationMinutes(computeDurationMinutes(request.startTime(), request.endTime()));
        task.setNotes(trimToNull(request.notes()));
        task.setDataSource("MANUAL");

        if (Boolean.TRUE.equals(request.reminderEnabled()) && request.startTime() != null) {
            task.setReminderEnabled(true);
            task.setReminderTime(request.startTime());
            task.setReminderAtUtc(computeTaskReminderAtUtc(userId, request.taskDate(), request.startTime(), null));
        }

        Task saved = taskRepository.save(task);
        ensureDayRecorded(userId, saved.getTaskDate());
        writeHistory(userId, saved.getId(), "CREATED", saved.getTaskDate(),
                "Created task \"" + saved.getTitle() + "\" (" + saved.getStatus() + ")");
        return toDto(saved);
    }

    @Transactional
    public TaskDto update(Long userId, Long taskId, TaskRequest request) {
        Task task = requireOwnedTask(userId, taskId);
        TaskCategory category = parseCategory(request.category());
        TaskPriority priority = parsePriority(request.priority());
        TaskStatus status = parseStatus(request.status(), parseStatus(task.getStatus(), TaskStatus.PLANNED));
        validateTimeRange(request.startTime(), request.endTime());

        String oldSnapshot = snapshotTask(task);

        task.setTitle(request.title().trim());
        task.setDescription(trimToNull(request.description()));
        task.setTaskDate(request.taskDate());
        task.setStartTime(request.startTime());
        task.setEndTime(request.endTime());
        task.setCategory(category.name());
        task.setPriority(priority.getRank());
        task.setStatus(status.name());
        task.setDurationMinutes(computeDurationMinutes(request.startTime(), request.endTime()));
        task.setNotes(trimToNull(request.notes()));

        if (request.reminderEnabled() != null) {
            if (Boolean.TRUE.equals(request.reminderEnabled()) && request.startTime() != null) {
                task.setReminderEnabled(true);
                task.setReminderTime(request.startTime());
            } else if (Boolean.FALSE.equals(request.reminderEnabled())) {
                task.setReminderEnabled(false);
                task.setReminderAtUtc(null);
            }
        }
        if (task.isReminderEnabled()) {
            Instant nextUtc = computeTaskReminderAtUtc(userId, task.getTaskDate(), task.getStartTime(), null);
            task.setReminderAtUtc(nextUtc);
            if (nextUtc == null) {
                task.setReminderEnabled(false);
            }
        }

        Task saved = taskRepository.save(task);
        String newSnapshot = snapshotTask(saved);
        writeHistory(userId, saved.getId(), "UPDATED", saved.getTaskDate(),
                "Old: " + oldSnapshot + " | New: " + newSnapshot);

        return toDto(saved);
    }

    @Transactional
    public TaskDto updateStatus(Long userId, Long taskId, TaskStatusRequest request) {
        Task task = requireOwnedTask(userId, taskId);
        TaskStatus newStatus = parseStatus(request.status(), null);
        if (newStatus == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Invalid task status.", Map.of("status", "Status must be PLANNED, COMPLETED, PENDING, SKIPPED, or RESCHEDULED."));
        }

        String oldStatus = task.getStatus();
        if (newStatus == TaskStatus.RESCHEDULED) {
            LocalDate targetDate = request.rescheduleToDate() != null
                    ? request.rescheduleToDate()
                    : task.getTaskDate().plusDays(1);
            if (!targetDate.isAfter(task.getTaskDate()) && targetDate.equals(task.getTaskDate())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "Choose a different date when rescheduling.",
                        Map.of("rescheduleToDate", "Target date must differ from the current task date."));
            }
            Task copied = createLinkedCopy(userId, task, targetDate);
            task.setStatus(TaskStatus.RESCHEDULED.name());
            task.setRescheduledToDate(targetDate);
            task.setRescheduledToTaskId(copied.getId());
            Task savedOriginal = taskRepository.save(task);

            writeHistory(userId, savedOriginal.getId(), "RESCHEDULED", savedOriginal.getTaskDate(),
                    "Status " + oldStatus + " -> RESCHEDULED to " + targetDate + " (new task #" + copied.getId() + ")");
            writeHistory(userId, copied.getId(), "CARRIED_FORWARD", targetDate,
                    "Rescheduled from " + savedOriginal.getTaskDate() + " (task #" + savedOriginal.getId() + ")");
            return toDto(savedOriginal);
        }

        task.setStatus(newStatus.name());
        Task saved = taskRepository.save(task);
        writeHistory(userId, saved.getId(), "STATUS_CHANGED", saved.getTaskDate(),
                "Status changed: " + oldStatus + " -> " + saved.getStatus());
        return toDto(saved);
    }

    @Transactional
    public TaskDto updateReminder(Long userId, Long taskId, TaskReminderRequest request) {
        Task task = requireOwnedTask(userId, taskId);
        if (request.enabled()) {
            if (task.getStartTime() == null) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "NO_START_TIME",
                        "Add a start time to this task before enabling a reminder.",
                        Map.of("startTime", "Start time is required for reminders.")
                );
            }
            task.setReminderEnabled(true);
            task.setReminderTime(request.reminderTime() != null ? request.reminderTime() : task.getStartTime());
            task.setReminderAtUtc(computeTaskReminderAtUtc(
                    userId, task.getTaskDate(), task.getStartTime(), request.leadMinutes()
            ));
        } else {
            task.setReminderEnabled(false);
            task.setReminderAtUtc(null);
        }
        Task saved = taskRepository.save(task);
        writeHistory(userId, saved.getId(), "REMINDER_UPDATED", saved.getTaskDate(),
                saved.isReminderEnabled()
                        ? "Reminder enabled (" + saved.getReminderAtUtc() + ")"
                        : "Reminder disabled");
        return toDto(saved);
    }

    @Transactional
    public void softDelete(Long userId, Long taskId) {
        Task task = requireOwnedTask(userId, taskId);
        task.setDeletedAtUtc(Instant.now());
        taskRepository.save(task);
        writeHistory(userId, task.getId(), "DELETED", task.getTaskDate(),
                "Soft-deleted task \"" + task.getTitle() + "\" (status was " + task.getStatus() + ")");
    }

    @Transactional
    public List<TaskDto> carryForward(Long userId, CarryForwardRequest request) {
        LocalDate targetDate = request.targetDate();
        List<TaskDto> created = new ArrayList<>();

        for (Long taskId : request.taskIds()) {
            Task original = requireOwnedTask(userId, taskId);
            String st = original.getStatus() == null ? "PLANNED" : original.getStatus().toUpperCase(Locale.ROOT);
            if (!UNFINISHED_STATUSES.contains(st)) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_CARRY_FORWARD",
                        "Only PLANNED or PENDING tasks can be carried forward.",
                        Map.of("taskIds", "Task \"" + original.getTitle() + "\" has status " + st)
                );
            }
            if (!targetDate.isAfter(original.getTaskDate()) && targetDate.equals(original.getTaskDate())) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_CARRY_FORWARD",
                        "Target date must be different from the original task date.",
                        Map.of("targetDate", "Target date must differ from " + original.getTaskDate())
                );
            }

            Task copied = createLinkedCopy(userId, original, targetDate);
            original.setStatus(TaskStatus.RESCHEDULED.name());
            original.setRescheduledToDate(targetDate);
            original.setRescheduledToTaskId(copied.getId());
            taskRepository.save(original);

            writeHistory(userId, original.getId(), "RESCHEDULED", original.getTaskDate(),
                    "Carried forward to " + targetDate + " (new task #" + copied.getId() + ")");
            writeHistory(userId, copied.getId(), "CARRIED_FORWARD", targetDate,
                    "Carried forward from " + original.getTaskDate() + " (task #" + original.getId() + ")");

            created.add(toDto(copied));
        }

        ensureDayRecorded(userId, targetDate);
        return created;
    }

    @Transactional(readOnly = true)
    public List<RecordHistoryDto> getTaskHistory(Long userId, Long taskId) {
        // Ensures another user cannot read this task's history (returns 404).
        Task task = taskRepository.findByIdAndUserIdAndDeletedAtUtcIsNull(taskId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "Task not found."));

        return recordHistoryRepository
                .findAllByUserIdAndEntityTypeAndEntityIdOrderByCreatedAtUtcDescIdDesc(userId, "TASK", task.getId())
                .stream()
                .map(h -> new RecordHistoryDto(
                        h.getId(),
                        h.getEntityType(),
                        h.getEntityId(),
                        h.getActionType(),
                        h.getActorSource(),
                        h.getRecordDate(),
                        h.getChangeSummary(),
                        h.getCreatedAtUtc()
                ))
                .toList();
    }

    private Task createLinkedCopy(Long userId, Task original, LocalDate targetDate) {
        Task copy = new Task();
        copy.setUserId(userId);
        copy.setTitle(original.getTitle());
        copy.setDescription(original.getDescription());
        copy.setTaskDate(targetDate);
        copy.setStartTime(original.getStartTime());
        copy.setEndTime(original.getEndTime());
        copy.setCategory(original.getCategory());
        copy.setPriority(original.getPriority());
        copy.setDurationMinutes(original.getDurationMinutes());
        copy.setNotes(original.getNotes());
        copy.setStatus(TaskStatus.PLANNED.name());
        copy.setCarriedFromTaskId(original.getId());
        copy.setReminderEnabled(original.isReminderEnabled());
        copy.setReminderTime(original.getReminderTime());
        if (copy.isReminderEnabled() && copy.getStartTime() != null) {
            copy.setReminderAtUtc(computeTaskReminderAtUtc(userId, targetDate, copy.getStartTime(), null));
        }
        copy.setDataSource("MANUAL");
        return taskRepository.save(copy);
    }

    private Instant computeTaskReminderAtUtc(
            Long userId,
            LocalDate taskDate,
            LocalTime startTime,
            Integer overrideLeadMinutes
    ) {
        if (taskDate == null || startTime == null) {
            return null;
        }
        int leadMinutes = overrideLeadMinutes != null
                ? Math.max(0, overrideLeadMinutes)
                : notificationPreferencesRepository.findByUserId(userId)
                        .map(NotificationPreferences::getLeadMinutes)
                        .orElse(10);
        String timeZone = userProfileRepository.findByUserId(userId)
                .map(UserProfile::getTimeZone)
                .orElse(UserProfile.DEFAULT_TIME_ZONE);
        return ReminderTimeCalculator.computeReminderAtUtc(taskDate, startTime, leadMinutes, timeZone);
    }

    private Task requireOwnedTask(Long userId, Long taskId) {
        return taskRepository.findByIdAndUserIdAndDeletedAtUtcIsNull(taskId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "Task not found."));
    }

    private void ensureDayRecorded(Long userId, LocalDate date) {
        if (dailyLogRepository.findByUserIdAndLogDate(userId, date).isEmpty()) {
            DailyLog log = new DailyLog();
            log.setUserId(userId);
            log.setLogDate(date);
            log.setDayStatus("RECORDED");
            log.setDataSource("MANUAL");
            dailyLogRepository.save(log);
        }
    }

    private void writeHistory(Long userId, Long taskId, String actionType, LocalDate date, String summary) {
        RecordHistory history = new RecordHistory();
        history.setUserId(userId);
        history.setEntityType("TASK");
        history.setEntityId(taskId);
        history.setActionType(actionType);
        history.setActorSource("MANUAL");
        history.setRecordDate(date);
        history.setChangeSummary(summary);
        recordHistoryRepository.save(history);
    }

    private void validateTimeRange(LocalTime startTime, LocalTime endTime) {
        if (startTime != null && endTime != null && !endTime.isAfter(startTime)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "End time must be after start time.",
                    Map.of("endTime", "End time must be after start time.")
            );
        }
    }

    private Integer computeDurationMinutes(LocalTime startTime, LocalTime endTime) {
        if (startTime == null || endTime == null) {
            return null;
        }
        return (int) ChronoUnit.MINUTES.between(startTime, endTime);
    }

    private TaskCategory parseCategory(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Category is required.",
                    Map.of("category", "Category is required."));
        }
        try {
            return TaskCategory.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid task category.",
                    Map.of("category", "Unsupported category: " + raw));
        }
    }

    private TaskPriority parsePriority(String raw) {
        if (raw == null || raw.isBlank()) {
            return TaskPriority.MEDIUM;
        }
        try {
            return TaskPriority.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid priority.",
                    Map.of("priority", "Priority must be LOW, MEDIUM, or HIGH."));
        }
    }

    private TaskStatus parseStatus(String raw, TaskStatus defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return TaskStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid status.",
                    Map.of("status", "Invalid task status: " + raw));
        }
    }

    private String snapshotTask(Task task) {
        return "[title=\"" + task.getTitle()
                + "\", category=" + task.getCategory()
                + ", date=" + task.getTaskDate()
                + ", time=" + task.getStartTime() + "-" + task.getEndTime()
                + ", priority=" + TaskPriority.fromRank(task.getPriority()).name()
                + ", status=" + task.getStatus()
                + "]";
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public TaskDto toDto(Task task) {
        String colorGroup = "Personal";
        if (task.getCategory() != null) {
            try {
                colorGroup = TaskCategory.valueOf(task.getCategory().toUpperCase(Locale.ROOT)).getColorGroup();
            } catch (IllegalArgumentException ignored) {
                colorGroup = "Study";
            }
        }
        return new TaskDto(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getTaskDate(),
                task.getStartTime(),
                task.getEndTime(),
                task.getStatus(),
                task.getCategory(),
                colorGroup,
                TaskPriority.fromRank(task.getPriority()).name(),
                task.getDurationMinutes(),
                task.getNotes(),
                task.getCarriedFromTaskId(),
                task.getRescheduledToDate(),
                task.getRescheduledToTaskId(),
                task.isReminderEnabled(),
                task.getReminderTime(),
                task.getReminderAtUtc(),
                task.getDataSource(),
                task.getCreatedAtUtc(),
                task.getUpdatedAtUtc()
        );
    }
}
