package com.pathpilot.job;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    List<JobApplication> findTop3ByUserIdOrderByAppliedDateDescIdDesc(Long userId);

    List<JobApplication> findAllByUserIdAndAppliedDateBetween(Long userId, LocalDate startDate, LocalDate endDate);
}
