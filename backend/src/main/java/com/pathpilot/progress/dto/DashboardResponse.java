package com.pathpilot.progress.dto;

import com.pathpilot.job.dto.JobDtos.JobFollowupDto;

import java.time.LocalDate;
import java.util.List;

public record DashboardResponse(
        LocalDate date,
        String timeZone,
        String greetingName,
        String targetRole,
        boolean demo,
        TodaySection today,
        MonthlySection monthly,
        CodingStreakSection codingStreak,
        boolean hasGithubActivity,
        List<GithubWeekDto> githubWeeks,
        CurrentProjectDto currentProject,
        List<RecentApplicationDto> recentApplications,
        List<JobFollowupDto> upcomingFollowups,
        QuickLinksDto links,
        RecommendationDto recommendation
) {
    public record TodaySection(
            int plannedCount,
            int completedCount,
            int pendingCount,
            int skippedCount,
            int rescheduledCount,
            Double completionRate,
            CategoryRatesDto categoryRates,
            List<FocusTaskDto> focusTasks
    ) {
    }

    public record CategoryRatesDto(
            Double learning,
            Double practice,
            Double projects,
            Double applications
    ) {
    }

    public record FocusTaskDto(
            Long id,
            String title,
            String category,
            String status,
            int priority,
            Integer durationMinutes,
            String dataSource
    ) {
    }

    public record MonthlySection(
            String month,
            int plannedCount,
            int completedCount,
            Double completionRate
    ) {
    }

    public record CodingStreakSection(
            int days,
            boolean recordedToday,
            String message,
            String dataSource
    ) {
    }

    public record GithubDayDto(
            LocalDate date,
            int count,
            int level
    ) {
    }

    public record GithubWeekDto(
            List<GithubDayDto> days
    ) {
    }

    public record CurrentProjectDto(
            Long id,
            String name,
            String status,
            int completedMilestones,
            int totalMilestones,
            Double progress,
            String dataSource
    ) {
    }

    public record RecentApplicationDto(
            Long id,
            String companyName,
            String roleTitle,
            String status,
            LocalDate appliedDate,
            String location,
            String dataSource
    ) {
    }

    public record QuickLinksDto(
            String portfolioUrl,
            String githubUrl,
            String linkedinUrl,
            String codingProfileUrl,
            String leetcodeUrl,
            String codingbatUrl
    ) {
    }

    public record RecommendationDto(
            String text,
            String source,
            String actionLabel,
            String actionPath
    ) {
    }
}
