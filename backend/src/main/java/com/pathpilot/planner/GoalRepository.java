package com.pathpilot.planner;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface GoalRepository extends JpaRepository<Goal, Long> {

    List<Goal> findAllByUserIdAndPeriodTypeAndStartDateAndDeletedAtUtcIsNullOrderByIdAsc(
            Long userId,
            String periodType,
            LocalDate startDate
    );

    Optional<Goal> findByIdAndUserIdAndDeletedAtUtcIsNull(Long id, Long userId);
}
