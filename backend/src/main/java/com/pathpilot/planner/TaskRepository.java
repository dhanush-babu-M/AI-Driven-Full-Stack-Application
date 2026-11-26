package com.pathpilot.planner;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findAllByUserIdAndTaskDateAndDeletedAtUtcIsNullOrderByPriorityAscStartTimeAscIdAsc(
            Long userId,
            LocalDate taskDate
    );

    List<Task> findAllByUserIdAndTaskDateBetweenAndDeletedAtUtcIsNull(
            Long userId,
            LocalDate startDate,
            LocalDate endDate
    );

    Optional<Task> findByIdAndUserIdAndDeletedAtUtcIsNull(Long id, Long userId);

    List<Task> findAllByUserIdAndTaskDateBeforeAndStatusInAndDeletedAtUtcIsNullOrderByTaskDateDescPriorityAscIdAsc(
            Long userId,
            LocalDate beforeDate,
            Collection<String> statuses
    );

    List<Task> findAllByUserIdAndGoalIdAndDeletedAtUtcIsNull(Long userId, Long goalId);

    List<Task> findAllByUserIdAndReminderEnabledTrueAndStatusInAndDeletedAtUtcIsNullOrderByReminderAtUtcAsc(
            Long userId,
            Collection<String> statuses
    );

    List<Task> findAllByUserIdAndReminderEnabledTrueAndReminderAtUtcBetweenAndStatusInAndDeletedAtUtcIsNullOrderByReminderAtUtcAsc(
            Long userId,
            Instant fromUtc,
            Instant toUtc,
            Collection<String> statuses
    );
}
