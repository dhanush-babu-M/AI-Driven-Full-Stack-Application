package com.pathpilot.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectMediaRepository extends JpaRepository<ProjectMedia, Long> {

    List<ProjectMedia> findAllByUserIdAndProjectIdOrderByCreatedAtUtcDescIdDesc(Long userId, Long projectId);

    Optional<ProjectMedia> findByIdAndUserIdAndProjectId(Long id, Long userId, Long projectId);

    long countByUserIdAndProjectIdAndMediaType(Long userId, Long projectId, String mediaType);
}
