package com.pathpilot.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ProjectWorklogRepository extends JpaRepository<ProjectWorklog, Long> {

    List<ProjectWorklog> findAllByUserIdAndProjectIdAndDeletedAtUtcIsNullOrderByWorkDateDescIdDesc(
            Long userId,
            Long projectId
    );

    Optional<ProjectWorklog> findByIdAndUserIdAndProjectIdAndDeletedAtUtcIsNull(
            Long id,
            Long userId,
            Long projectId
    );

    List<ProjectWorklog> findAllByUserIdAndWorkDateAndDeletedAtUtcIsNullOrderByCreatedAtUtcDescIdDesc(
            Long userId,
            LocalDate workDate
    );

    List<ProjectWorklog> findAllByUserIdAndDeletedAtUtcIsNullAndWorkDateBetweenOrderByWorkDateDescIdDesc(
            Long userId,
            LocalDate startDate,
            LocalDate endDate
    );
}
