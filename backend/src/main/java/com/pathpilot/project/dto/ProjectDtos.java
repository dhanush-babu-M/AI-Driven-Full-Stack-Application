package com.pathpilot.project.dto;

import com.pathpilot.planner.dto.RecordHistoryDto;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ProjectDtos {

    private ProjectDtos() {
    }

    public record ProjectRequest(
            @NotBlank(message = "Project name is required.")
            @Size(max = 200, message = "Project name must be 200 characters or fewer.")
            String title,

            @Size(max = 40, message = "Icon must be 40 characters or fewer.")
            String icon,

            @Size(max = 4000, message = "Description must be 4000 characters or fewer.")
            String description,

            List<@Size(max = 50, message = "Each technology tag must be 50 characters or fewer.") String> techStack,

            @Size(max = 500, message = "Live Demo URL must be 500 characters or fewer.")
            String liveUrl,

            @Size(max = 500, message = "GitHub URL must be 500 characters or fewer.")
            String repoUrl,

            @Size(max = 30, message = "Status must be 30 characters or fewer.")
            String status,

            LocalDate startDate,

            LocalDate targetDate
    ) {
    }

    public record ProjectStatusRequest(
            @NotBlank(message = "Status is required.")
            @Size(max = 30, message = "Status must be 30 characters or fewer.")
            String status,

            @Size(max = 500, message = "Note must be 500 characters or fewer.")
            String note
    ) {
    }

    public record ProjectSummaryDto(
            Long id,
            String title,
            String icon,
            String description,
            List<String> techStack,
            String liveUrl,
            String repoUrl,
            String status,
            LocalDate startDate,
            LocalDate targetDate,
            int completedMilestones,
            int totalMilestones,
            Double progress,
            int worklogCount,
            int mediaCount,
            String dataSource,
            Instant createdAtUtc,
            Instant updatedAtUtc
    ) {
    }

    public record ProjectDetailDto(
            ProjectSummaryDto project,
            List<ProjectMilestoneDto> milestones,
            List<ProjectWorklogDto> worklogs,
            List<ProjectMediaDto> media,
            List<RecordHistoryDto> statusHistory
    ) {
    }

    public record ProjectMilestoneRequest(
            @NotBlank(message = "Milestone title is required.")
            @Size(max = 200, message = "Milestone title must be 200 characters or fewer.")
            String title,

            LocalDate dueDate,

            LocalDate completedDate,

            @Size(max = 20, message = "Status must be 20 characters or fewer.")
            String status,

            @Min(value = 0, message = "Sort order cannot be negative.")
            Integer sortOrder,

            @Size(max = 2000, message = "Notes must be 2000 characters or fewer.")
            String notes
    ) {
    }

    public record MilestoneCompleteRequest(
            boolean completed,
            LocalDate completedDate
    ) {
    }

    public record ProjectMilestoneDto(
            Long id,
            Long projectId,
            String title,
            LocalDate dueDate,
            LocalDate completedDate,
            String status,
            int sortOrder,
            String notes,
            String dataSource,
            Instant createdAtUtc,
            Instant updatedAtUtc
    ) {
    }

    public record ProjectWorklogRequest(
            @NotNull(message = "Work date is required.")
            LocalDate workDate,

            @Min(value = 0, message = "Minutes spent cannot be negative.")
            @Max(value = 1440, message = "Minutes spent must be 1440 or fewer.")
            Integer minutesSpent,

            @NotBlank(message = "Work summary is required.")
            @Size(max = 4000, message = "Work summary must be 4000 characters or fewer.")
            String summary
    ) {
    }

    public record ProjectWorklogDto(
            Long id,
            Long projectId,
            String projectTitle,
            LocalDate workDate,
            int minutesSpent,
            String summary,
            String dataSource,
            Instant createdAtUtc,
            Instant updatedAtUtc
    ) {
    }

    public record VideoLinkRequest(
            @NotBlank(message = "Demo video URL is required.")
            @Size(max = 500, message = "Video URL must be 500 characters or fewer.")
            String videoUrl,

            @Size(max = 255, message = "Caption must be 255 characters or fewer.")
            String caption
    ) {
    }

    public record ProjectMediaDto(
            Long id,
            Long projectId,
            String mediaType,
            String fileUrl,
            String originalFilename,
            String contentType,
            Long fileSizeBytes,
            String videoUrl,
            String caption,
            Instant createdAtUtc
    ) {
    }
}
