package com.pathpilot.job;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface JobFollowupRepository extends JpaRepository<JobFollowup, Long> {

    List<JobFollowup> findAllByUserIdAndJobApplicationIdAndDeletedAtUtcIsNullOrderByDueDateAscIdAsc(
            Long userId,
            Long jobApplicationId
    );

    List<JobFollowup> findAllByUserIdAndStatusAndDeletedAtUtcIsNullAndDueDateLessThanEqualOrderByDueDateAscIdAsc(
            Long userId,
            String status,
            LocalDate maxDueDate
    );

    List<JobFollowup> findAllByUserIdAndStatusAndDeletedAtUtcIsNullOrderByDueDateAscIdAsc(
            Long userId,
            String status
    );

    List<JobFollowup> findAllByUserIdAndStatusAndDeletedAtUtcIsNullAndReminderAtUtcBetweenOrderByReminderAtUtcAsc(
            Long userId,
            String status,
            Instant fromUtc,
            Instant toUtc
    );

    Optional<JobFollowup> findByIdAndUserIdAndDeletedAtUtcIsNull(Long id, Long userId);
}
