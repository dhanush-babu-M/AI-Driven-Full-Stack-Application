package com.pathpilot.job.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class JobDtos {

    private JobDtos() {
    }

    public record PrepChecklistItemDto(
            @Size(max = 80, message = "Item ID must be 80 characters or fewer.")
            String id,

            @NotBlank(message = "Checklist item text is required.")
            @Size(max = 200, message = "Checklist item must be 200 characters or fewer.")
            String text,

            boolean done
    ) {
    }

    public record JobApplicationRequest(
            @NotBlank(message = "Company name is required.")
            @Size(max = 200, message = "Company name must be 200 characters or fewer.")
            String companyName,

            @NotBlank(message = "Job title is required.")
            @Size(max = 200, message = "Job title must be 200 characters or fewer.")
            String roleTitle,

            @Size(max = 40, message = "Status must be 40 characters or fewer.")
            String status,

            @NotNull(message = "Applied date is required.")
            LocalDate appliedDate,

            @Size(max = 500, message = "Job link must be 500 characters or fewer.")
            String jobUrl,

            @Size(max = 20000, message = "Job description must be 20,000 characters or fewer.")
            String jobDescription,

            @Size(max = 150, message = "Location must be 150 characters or fewer.")
            String location,

            @Size(max = 40, message = "Source must be 40 characters or fewer.")
            String source,

            @Size(max = 4000, message = "Recruiter notes must be 4000 characters or fewer.")
            String recruiterNotes,

            LocalDate followupDate,

            LocalDate interviewDate,

            @Size(max = 4000, message = "Personal notes must be 4000 characters or fewer.")
            String notes,

            List<@Valid PrepChecklistItemDto> prepChecklist
    ) {
    }

    public record JobStatusChangeRequest(
            @NotBlank(message = "Status is required.")
            @Size(max = 40, message = "Status must be 40 characters or fewer.")
            String status,

            @Size(max = 500, message = "Note must be 500 characters or fewer.")
            String note
    ) {
    }

    public record JobChecklistRequest(
            @NotNull(message = "Checklist items are required.")
            List<@Valid PrepChecklistItemDto> items
    ) {
    }

    public record FollowupRescheduleRequest(
            @NotNull(message = "New follow-up date is required.")
            LocalDate dueDate,

            @Size(max = 500, message = "Note must be 500 characters or fewer.")
            String note
    ) {
    }

    public record JobApplicationSummaryDto(
            Long id,
            String companyName,
            String roleTitle,
            String status,
            LocalDate appliedDate,
            String jobUrl,
            String location,
            String source,
            LocalDate followupDate,
            LocalDate interviewDate,
            String dataSource,
            Instant createdAtUtc,
            Instant updatedAtUtc
    ) {
    }

    public record JobStatusHistoryDto(
            Long id,
            String previousStatus,
            String newStatus,
            LocalDate changedDate,
            String note,
            Instant changedAtUtc
    ) {
    }

    public record JobFollowupDto(
            Long id,
            Long jobApplicationId,
            String companyName,
            String roleTitle,
            String applicationStatus,
            LocalDate dueDate,
            boolean overdue,
            String note,
            String status,
            Instant completedAtUtc,
            Instant reminderAtUtc
    ) {
    }

    public record JobApplicationDetailDto(
            Long id,
            String companyName,
            String roleTitle,
            String status,
            LocalDate appliedDate,
            String jobUrl,
            String jobDescription,
            String location,
            String source,
            String recruiterNotes,
            LocalDate followupDate,
            LocalDate interviewDate,
            String notes,
            List<PrepChecklistItemDto> prepChecklist,
            String dataSource,
            Instant createdAtUtc,
            Instant updatedAtUtc,
            List<JobStatusHistoryDto> statusHistory,
            List<JobFollowupDto> followups
    ) {
    }

    public record JobPageResponse(
            List<JobApplicationSummaryDto> items,
            int page,
            int size,
            long totalItems,
            int totalPages
    ) {
    }

    public record JobStatsDto(
            long total,
            long interested,
            long toApply,
            long applied,
            long followupNeeded,
            long assessment,
            long interviews,
            long offers,
            long rejected,
            long followupsDueThisWeek,
            Map<String, Long> statusCounts
    ) {
    }
}
