package com.pathpilot.planner;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecordHistoryRepository extends JpaRepository<RecordHistory, Long> {

    List<RecordHistory> findAllByUserIdAndEntityTypeAndEntityIdOrderByCreatedAtUtcDescIdDesc(
            Long userId,
            String entityType,
            Long entityId
    );
}
