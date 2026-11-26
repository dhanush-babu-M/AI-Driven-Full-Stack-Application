package com.pathpilot.progress;

import com.pathpilot.progress.dto.DashboardResponse;
import com.pathpilot.progress.dto.PeriodProgressDtos.MonthlyProgressResponse;
import com.pathpilot.progress.dto.PeriodProgressDtos.WeeklyProgressResponse;
import com.pathpilot.security.CurrentUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/progress")
public class DashboardController {

    private final DashboardService dashboardService;
    private final PeriodProgressService periodProgressService;

    public DashboardController(DashboardService dashboardService, PeriodProgressService periodProgressService) {
        this.dashboardService = dashboardService;
        this.periodProgressService = periodProgressService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> getDashboard(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date
    ) {
        DashboardResponse response = dashboardService.getDashboard(CurrentUser.id(), date);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/weekly")
    public ResponseEntity<WeeklyProgressResponse> getWeekly(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate start
    ) {
        return ResponseEntity.ok(periodProgressService.getWeeklyProgress(CurrentUser.id(), start));
    }

    @GetMapping("/monthly")
    public ResponseEntity<MonthlyProgressResponse> getMonthly(
            @RequestParam(required = false) String month
    ) {
        return ResponseEntity.ok(periodProgressService.getMonthlyProgress(CurrentUser.id(), month));
    }
}
