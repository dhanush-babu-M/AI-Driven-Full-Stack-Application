package com.pathpilot.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MonthlyReportRepository extends JpaRepository<MonthlyReport, Long> {

    Optional<MonthlyReport> findByUserIdAndReportMonth(Long userId, String reportMonth);
}
