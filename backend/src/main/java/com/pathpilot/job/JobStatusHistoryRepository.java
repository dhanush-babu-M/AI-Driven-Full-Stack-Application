package com.pathpilot.job;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobStatusHistoryRepository extends JpaRepository<JobStatusHistory, Long> {

    List<JobStatusHistory> findAllByUserIdAndJobApplicationIdOrderByChangedAtUtcDescIdDesc(
            Long userId,
            Long jobApplicationId
    );
}
