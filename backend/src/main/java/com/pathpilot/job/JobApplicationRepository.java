package com.pathpilot.job;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    List<JobApplication> findTop3ByUserIdOrderByAppliedDateDescIdDesc(Long userId);

    List<JobApplication> findTop3ByUserIdAndDeletedAtUtcIsNullOrderByAppliedDateDescIdDesc(Long userId);

    List<JobApplication> findAllByUserIdAndAppliedDateBetween(Long userId, LocalDate startDate, LocalDate endDate);

    List<JobApplication> findAllByUserIdAndDeletedAtUtcIsNullAndAppliedDateBetween(
            Long userId,
            LocalDate startDate,
            LocalDate endDate
    );

    List<JobApplication> findAllByUserIdAndDeletedAtUtcIsNullOrderByAppliedDateDescIdDesc(Long userId);

    Optional<JobApplication> findByIdAndUserIdAndDeletedAtUtcIsNull(Long id, Long userId);
}
