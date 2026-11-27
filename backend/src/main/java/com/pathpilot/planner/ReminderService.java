package com.pathpilot.planner;

import com.pathpilot.job.JobApplication;
import com.pathpilot.job.JobApplicationRepository;
import com.pathpilot.job.JobFollowup;
import com.pathpilot.job.JobFollowupRepository;
import com.pathpilot.planner.dto.ReminderDtos.NotificationPreferencesDto;
import com.pathpilot.planner.dto.ReminderDtos.NotificationPreferencesRequest;
import com.pathpilot.planner.dto.ReminderDtos.UpcomingReminderDto;
import com.pathpilot.user.UserProfile;
import com.pathpilot.user.UserProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReminderService {

    private static final List<String> ACTIVE_STATUSES = List.of("PLANNED", "PENDING");

    private final NotificationPreferencesRepository preferencesRepository;
    private final TaskRepository taskRepository;
    private final JobFollowupRepository jobFollowupRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final UserProfileRepository userProfileRepository;

    public ReminderService(
            NotificationPreferencesRepository preferencesRepository,
            TaskRepository taskRepository,
            JobFollowupRepository jobFollowupRepository,
            JobApplicationRepository jobApplicationRepository,
            UserProfileRepository userProfileRepository
    ) {
        this.preferencesRepository = preferencesRepository;
        this.taskRepository = taskRepository;
        this.jobFollowupRepository = jobFollowupRepository;
        this.jobApplicationRepository = jobApplicationRepository;
        this.userProfileRepository = userProfileRepository;
    }

    @Transactional(readOnly = true)
    public NotificationPreferencesDto getPreferences(Long userId) {
        NotificationPreferences prefs = preferencesRepository.findByUserId(userId)
                .orElseGet(() -> defaultPrefs(userId));
        return toDto(prefs);
    }

    @Transactional
    public NotificationPreferencesDto updatePreferences(Long userId, NotificationPreferencesRequest request) {
        NotificationPreferences prefs = preferencesRepository.findByUserId(userId)
                .orElseGet(() -> defaultPrefs(userId));

        prefs.setBrowserEnabled(request.browserEnabled());
        prefs.setLeadMinutes(request.leadMinutes());
        prefs.setQuietHoursStart(request.quietHoursStart());
        prefs.setQuietHoursEnd(request.quietHoursEnd());

        NotificationPreferences saved = preferencesRepository.save(prefs);

        // Recalculate reminder_at_utc for all active reminder-enabled tasks for this user.
        String timeZone = resolveTimeZone(userId);
        List<Task> reminderTasks = taskRepository
                .findAllByUserIdAndReminderEnabledTrueAndStatusInAndDeletedAtUtcIsNullOrderByReminderAtUtcAsc(
                        userId, ACTIVE_STATUSES
                );
        for (Task task : reminderTasks) {
            Instant updatedAtUtc = ReminderTimeCalculator.computeReminderAtUtc(
                    task.getTaskDate(),
                    task.getStartTime(),
                    saved.getLeadMinutes(),
                    timeZone
            );
            task.setReminderAtUtc(updatedAtUtc);
            if (updatedAtUtc == null) {
                task.setReminderEnabled(false);
            }
            taskRepository.save(task);
        }

        // Recalculate reminder_at_utc for pending job follow-ups.
        List<JobFollowup> pendingFollowups = jobFollowupRepository
                .findAllByUserIdAndStatusAndDeletedAtUtcIsNullOrderByDueDateAscIdAsc(
                        userId, JobFollowup.STATUS_PENDING
                );
        for (JobFollowup followup : pendingFollowups) {
            Instant updatedAtUtc = ReminderTimeCalculator.computeReminderAtUtc(
                    followup.getDueDate(),
                    followup.getReminderTime() != null ? followup.getReminderTime() : JobFollowup.DEFAULT_REMINDER_TIME,
                    saved.getLeadMinutes(),
                    timeZone
            );
            followup.setReminderAtUtc(updatedAtUtc);
            jobFollowupRepository.save(followup);
        }

        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<UpcomingReminderDto> getUpcomingReminders(Long userId, int hours) {
        int clampedHours = Math.max(1, Math.min(168, hours));
        String timeZone = resolveTimeZone(userId);
        ZoneId zoneId = PeriodBoundaryHelper.safeZone(timeZone);
        NotificationPreferences prefs = preferencesRepository.findByUserId(userId)
                .orElseGet(() -> defaultPrefs(userId));

        Instant now = Instant.now();
        LocalDate localToday = PeriodBoundaryHelper.localToday(now, timeZone);
        Instant todayStartUtc = localToday.atStartOfDay(zoneId).toInstant();
        Instant horizonUtc = now.plus(clampedHours, ChronoUnit.HOURS);

        List<UpcomingReminderDto> results = new ArrayList<>();

        List<Task> tasks = taskRepository
                .findAllByUserIdAndReminderEnabledTrueAndReminderAtUtcBetweenAndStatusInAndDeletedAtUtcIsNullOrderByReminderAtUtcAsc(
                        userId, todayStartUtc, horizonUtc, ACTIVE_STATUSES
                );
        for (Task task : tasks) {
            results.add(new UpcomingReminderDto(
                    task.getId(),
                    task.getTitle(),
                    task.getCategory(),
                    task.getTaskDate(),
                    task.getStartTime(),
                    task.getEndTime(),
                    task.getReminderAtUtc(),
                    task.getStatus(),
                    ReminderTimeCalculator.isWithinQuietHours(
                            task.getReminderAtUtc(),
                            timeZone,
                            prefs.getQuietHoursStart(),
                            prefs.getQuietHoursEnd()
                    ),
                    "TASK",
                    null,
                    null,
                    "/app/planner/daily?date=" + task.getTaskDate()
            ));
        }

        // Include pending job follow-ups due today or tomorrow (or within the requested horizon).
        LocalDate tomorrow = localToday.plusDays(1);
        List<JobFollowup> followups = jobFollowupRepository
                .findAllByUserIdAndStatusAndDeletedAtUtcIsNullAndDueDateLessThanEqualOrderByDueDateAscIdAsc(
                        userId, JobFollowup.STATUS_PENDING, tomorrow
                );
        if (!followups.isEmpty()) {
            Map<Long, JobApplication> jobsById = jobApplicationRepository
                    .findAllByUserIdAndDeletedAtUtcIsNullOrderByAppliedDateDescIdDesc(userId)
                    .stream()
                    .collect(Collectors.toMap(JobApplication::getId, a -> a, (a, b) -> a));

            for (JobFollowup f : followups) {
                JobApplication app = jobsById.get(f.getJobApplicationId());
                if (app == null) continue;
                Instant reminderAtUtc = f.getReminderAtUtc();
                if (reminderAtUtc == null) {
                    reminderAtUtc = ReminderTimeCalculator.computeReminderAtUtc(
                            f.getDueDate(),
                            f.getReminderTime() != null ? f.getReminderTime() : JobFollowup.DEFAULT_REMINDER_TIME,
                            prefs.getLeadMinutes(),
                            timeZone
                    );
                }
                results.add(new UpcomingReminderDto(
                        null,
                        "Follow up: " + app.getCompanyName() + " — " + app.getRoleTitle(),
                        "JOB_FOLLOWUP",
                        f.getDueDate(),
                        f.getReminderTime() != null ? f.getReminderTime() : JobFollowup.DEFAULT_REMINDER_TIME,
                        null,
                        reminderAtUtc,
                        f.getStatus(),
                        ReminderTimeCalculator.isWithinQuietHours(
                                reminderAtUtc,
                                timeZone,
                                prefs.getQuietHoursStart(),
                                prefs.getQuietHoursEnd()
                        ),
                        "FOLLOWUP",
                        f.getId(),
                        app.getId(),
                        "/app/projects?tab=applications&jobId=" + app.getId()
                ));
            }
        }

        results.sort(Comparator.comparing(
                UpcomingReminderDto::reminderAtUtc,
                Comparator.nullsLast(Comparator.naturalOrder())
        ));
        return results;
    }

    private NotificationPreferences defaultPrefs(Long userId) {
        NotificationPreferences prefs = new NotificationPreferences();
        prefs.setUserId(userId);
        prefs.setBrowserEnabled(false);
        prefs.setLeadMinutes(10);
        return prefs;
    }

    private NotificationPreferencesDto toDto(NotificationPreferences prefs) {
        return new NotificationPreferencesDto(
                prefs.isBrowserEnabled(),
                prefs.getLeadMinutes(),
                prefs.getQuietHoursStart(),
                prefs.getQuietHoursEnd()
        );
    }

    private String resolveTimeZone(Long userId) {
        return userProfileRepository.findByUserId(userId)
                .map(UserProfile::getTimeZone)
                .filter(tz -> tz != null && !tz.isBlank())
                .orElse(UserProfile.DEFAULT_TIME_ZONE);
    }
}
