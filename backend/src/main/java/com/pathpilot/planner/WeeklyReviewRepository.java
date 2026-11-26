package com.pathpilot.planner;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface WeeklyReviewRepository extends JpaRepository<WeeklyReview, Long> {

    Optional<WeeklyReview> findByUserIdAndWeekStartDate(Long userId, LocalDate weekStartDate);
}
