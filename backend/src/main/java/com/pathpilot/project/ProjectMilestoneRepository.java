package com.pathpilot.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ProjectMilestoneRepository extends JpaRepository<ProjectMilestone, Long> {

    List<ProjectMilestone> findAllByUserIdAndProjectIdOrderBySortOrderAscIdAsc(Long userId, Long projectId);

    List<ProjectMilestone> findAllByUserIdAndDueDateBetweenOrderByDueDateAscIdAsc(
            Long userId,
            LocalDate startDate,
            LocalDate endDate
    );
}
