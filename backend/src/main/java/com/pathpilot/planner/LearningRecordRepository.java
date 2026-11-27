package com.pathpilot.planner;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface LearningRecordRepository extends JpaRepository<LearningRecord, Long> {

    List<LearningRecord> findAllByUserIdAndRecordDateOrderByCreatedAtUtcAscIdAsc(Long userId, LocalDate recordDate);

    List<LearningRecord> findAllByUserIdAndRecordDateBetweenOrderByRecordDateAscIdAsc(
            Long userId,
            LocalDate startDate,
            LocalDate endDate
    );
}
