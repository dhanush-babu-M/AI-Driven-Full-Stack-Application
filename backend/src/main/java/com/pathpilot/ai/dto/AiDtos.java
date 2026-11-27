package com.pathpilot.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class AiDtos {

    public static final String AI_DISCLAIMER = "AI-generated. Based on your saved records.";

    private AiDtos() {
    }

    public record GoalGapDto(
            String title,
            String category,
            int targetCount,
            int doneCount,
            int remainingCount
    ) {
    }

    public record DailyFactsDto(
            LocalDate date,
            Integer daysSinceLastCoding,
            int overdueTasksCount,
            String topOverdueTaskTitle,
            int plannedTodayCount,
            int completedTodayCount,
            int pendingTodayCount,
            int upcomingFollowupsCount,
            List<GoalGapDto> weeklyGoalGaps
    ) {
    }

    public record ProposedTaskDraftDto(
            String title,
            String description,
            String category,
            LocalDate taskDate,
            String priority
    ) {
    }

    public record DailyRecommendationResponse(
            String text,
            String source,
            String disclaimer,
            String notice,
            boolean cached,
            DailyFactsDto facts,
            ProposedTaskDraftDto proposedTask,
            String actionLabel,
            String actionPath
    ) {
    }

    public record CreateTaskProposalRequest(
            @NotBlank(message = "Task title is required.")
            @Size(max = 200, message = "Task title must be 200 characters or fewer.")
            String title,

            @Size(max = 2000, message = "Description must be 2000 characters or fewer.")
            String description,

            @Size(max = 50, message = "Category must be 50 characters or fewer.")
            String category,

            LocalDate taskDate,

            @Size(max = 20, message = "Priority must be 20 characters or fewer.")
            String priority
    ) {
    }

    public record CreateBatchTaskProposalsRequest(
            @NotEmpty(message = "At least one task proposal is required.")
            List<@Valid CreateTaskProposalRequest> tasks
    ) {
    }

    public record AiProposalDto(
            Long id,
            String targetEntity,
            String proposalType,
            String title,
            String category,
            LocalDate taskDate,
            String description,
            String priority,
            String status,
            String dataSource,
            LocalDate proposalDate,
            Instant createdAtUtc,
            Instant reviewedAtUtc
    ) {
    }

    public record JdAnalysisRequest(
            Long jobApplicationId,

            @Size(max = 12000, message = "Job description must be 12,000 characters or fewer.")
            String jobDescription
    ) {
    }

    public record InterviewQuestionsDto(
            List<String> java,
            List<String> springBoot,
            List<String> sql,
            List<String> react,
            List<String> behavioural
    ) {
    }

    public record JdAnalysisResponse(
            Long jobApplicationId,
            String source,
            String disclaimer,
            String notice,
            List<String> requiredSkills,
            List<String> niceToHave,
            List<String> matchedSkills,
            List<String> gaps,
            List<String> weakAreas,
            List<String> preparationChecklist,
            List<String> recommendedProjectsToShow,
            InterviewQuestionsDto interviewQuestions,
            Instant analyzedAtUtc
    ) {
    }

    public record MonthlySummaryResponse(
            String month,
            String source,
            String disclaimer,
            String notice,
            boolean cached,
            List<String> summaryParagraphs,
            List<String> wentWell,
            List<String> unfinished,
            List<String> nextMonthPriorities,
            Instant generatedAtUtc
    ) {
    }

    public record AgentChatRequest(
            @NotBlank(message = "Ask a question or request a plan.")
            @Size(max = 1500, message = "Message must be 1500 characters or fewer.")
            String message
    ) {
    }

    public record AgentToolTraceDto(
            String toolName,
            Map<String, Object> arguments,
            String summary
    ) {
    }

    public record AgentChatResponse(
            String reply,
            String source,
            String disclaimer,
            String notice,
            List<AgentToolTraceDto> toolCalls,
            List<AiProposalDto> createdProposals
    ) {
    }
}
