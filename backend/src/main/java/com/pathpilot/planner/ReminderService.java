package com.pathpilot.planner;

import com.pathpilot.planner.dto.ReminderDtos.NotificationPreferencesDto;
import com.pathpilot.planner.dto.ReminderDtos.NotificationPreferencesRequest;
import com.pathpilot.planner.dto.ReminderDtos.UpcomingReminderDto;
import com.pathpilot.user.UserProfile;
import com.pathpilot.user.UserProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class ReminderService {

    private static final List<String> ACTIVE_STATUSES = List.of("PLANNED", "PENDING");

    private final NotificationPreferencesRepository preferencesRepository;
    private final TaskRepository taskRepository;
    private final UserProfileRepository userProfileRepository;

    public ReminderService(
            NotificationPreferencesRepository preferencesRepository,
            TaskRepository taskRepository,
            UserProfileRepository userProfileRepository
    ) {
        this.preferencesRepository = preferencesRepository;
        this.taskRepository = taskRepository;
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

        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<UpcomingReminderDto> getUpcomingReminders(Long userId, int hours) {
        int clampedHours = Math.max(1, Math.min(168, hours));
        String timeZone = resolveTimeZone(userId);
        NotificationPreferences prefs = preferencesRepository.findByUserId(userId)
                .orElseGet(() -> defaultPrefs(userId));

        Instant now = Instant.now();
        LocalDate localToday = PeriodBoundaryHelper.localToday(now, timeZone);
        Instant todayStartUtc = localToday.atStartOfDay(PeriodBoundaryHelper.safeZone(timeZone)).toInstant();
        Instant horizonUtc = now.plus(clampedHours, ChronoUnit.HOURS);

        List<Task> tasks = taskRepository
                .findAllByUserIdAndReminderEnabledTrueAndReminderAtUtcBetweenAndStatusInAndDeletedAtUtcIsNullOrderByReminderAtUtcAsc(
                        userId, todayStartUtc, horizonUtc, ACTIVE_STATUSES
                );

        return tasks.stream()
                .map(task -> new UpcomingReminderDto(
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
                        )
                ))
                .toList();
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
