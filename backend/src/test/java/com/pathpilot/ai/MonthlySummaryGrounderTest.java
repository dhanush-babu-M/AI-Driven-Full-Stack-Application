package com.pathpilot.ai;

import com.pathpilot.ai.LlmClient.LlmResult;
import com.pathpilot.ai.MonthlySummaryGrounder.MonthlySummaryPayload;
import com.pathpilot.ai.dto.AiDtos.MonthlySummaryResponse;
import com.pathpilot.coding.CodingActivityRepository;
import com.pathpilot.job.JobApplicationRepository;
import com.pathpilot.planner.GoalService;
import com.pathpilot.planner.LearningRecordRepository;
import com.pathpilot.planner.TaskRepository;
import com.pathpilot.project.ProjectMilestoneRepository;
import com.pathpilot.project.ProjectWorklogRepository;
import com.pathpilot.report.MonthlyReport;
import com.pathpilot.report.MonthlyReportRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MonthlySummaryGrounderTest {

    private final MonthlySummaryGrounder grounder = new MonthlySummaryGrounder();

    @Test
    void validate_rejectsOutputWithInventedNumbersNotInSnapshot() {
        Set<String> allowed = grounder.collectSnapshotNumbers(
                2026, 11,
                10, 7, 2, 1, 70.0,
                5, 6, 180, 16.7,
                4, 120,
                2, 90,
                1, 3,
                1, 2,
                3
        );

        MonthlySummaryPayload invented = new MonthlySummaryPayload(
                List.of("In 11/2026 you solved 99 coding problems and finished 7 of 10 tasks."),
                List.of("Completed 7 tasks."),
                List.of("2 pending tasks remain."),
                List.of("Keep building momentum.")
        );

        String error = grounder.validate(invented, allowed);
        assertNotNull(error);
        assertTrue(error.contains("99"));
    }

    @Test
    void validate_acceptsOutputWhenAllNumbersMatchSnapshot() {
        Set<String> allowed = grounder.collectSnapshotNumbers(
                2026, 11,
                10, 7, 2, 1, 70.0,
                5, 6, 180, 16.7,
                4, 120,
                2, 90,
                1, 3,
                1, 2,
                3
        );

        MonthlySummaryPayload grounded = new MonthlySummaryPayload(
                List.of("In 2026-11 you completed 7 of 10 planned tasks (70%) and logged 6 coding entries across 5 days (3 hours)."),
                List.of("Submitted 3 job applications and completed 1 of 3 project milestones."),
                List.of("2 tasks remained pending and 1 was skipped."),
                List.of("Finish remaining 2 project milestones.")
        );

        assertNull(grounder.validate(grounded, allowed));
    }

    @Test
    void service_cachesValidAiSummaryAndRejectsInventedFallbackToRule() {
        TaskRepository taskRepo = mock(TaskRepository.class);
        CodingActivityRepository codingRepo = mock(CodingActivityRepository.class);
        LearningRecordRepository learningRepo = mock(LearningRecordRepository.class);
        ProjectMilestoneRepository milestoneRepo = mock(ProjectMilestoneRepository.class);
        ProjectWorklogRepository worklogRepo = mock(ProjectWorklogRepository.class);
        JobApplicationRepository jobRepo = mock(JobApplicationRepository.class);
        GoalService goalService = mock(GoalService.class);
        MonthlyReportRepository reportRepo = mock(MonthlyReportRepository.class);
        LlmClient llmClient = mock(LlmClient.class);

        when(taskRepo.findAllByUserIdAndDeletedAtUtcIsNullAndTaskDateBetweenOrderByTaskDateAscStartTimeAscIdAsc(anyLong(), any(), any()))
                .thenReturn(List.of());
        when(codingRepo.findAllByUserIdAndDeletedAtUtcIsNullAndActivityDateBetweenOrderByActivityDateDescIdDesc(anyLong(), any(), any()))
                .thenReturn(List.of());
        when(learningRepo.findAllByUserIdAndRecordDateBetweenOrderByRecordDateAscIdAsc(anyLong(), any(), any()))
                .thenReturn(List.of());
        when(milestoneRepo.findAllByUserIdAndDeletedAtUtcIsNullAndPlannedDateBetweenOrderByPlannedDateAscIdAsc(anyLong(), any(), any()))
                .thenReturn(List.of());
        when(worklogRepo.findAllByUserIdAndDeletedAtUtcIsNullAndWorkDateBetweenOrderByWorkDateDescIdDesc(anyLong(), any(), any()))
                .thenReturn(List.of());
        when(jobRepo.findAllByUserIdAndDeletedAtUtcIsNullOrderByAppliedDateDescIdDesc(anyLong()))
                .thenReturn(List.of());
        when(goalService.listGoals(anyLong(), any(), any())).thenReturn(List.of());
        when(reportRepo.findByUserIdAndReportMonth(anyLong(), any())).thenReturn(Optional.empty());
        when(reportRepo.save(any(MonthlyReport.class))).thenAnswer(inv -> inv.getArgument(0));

        when(llmClient.isConfigured()).thenReturn(true);
        // First response invents 42; second attempt also invents 77 -> falls back to RULE cleanly
        String hallucinated = """
                {
                  "summaryParagraphs": ["You logged 42 hours in 2026-11."],
                  "wentWell": ["42 hours recorded."],
                  "unfinished": [],
                  "nextMonthPriorities": ["Continue steady pace."]
                }
                """;
        when(llmClient.generate(any(), any()))
                .thenReturn(new LlmResult(hallucinated, "gemini-3.5-flash-lite", "gemini"));

        AiCacheService cache = new AiCacheService();
        ObjectMapper mapper = new ObjectMapper();
        MonthlySummaryService service = new MonthlySummaryService(
                taskRepo, codingRepo, learningRepo, milestoneRepo, worklogRepo, jobRepo,
                goalService, reportRepo, grounder, new AiStructuredExecutor(llmClient, mapper), cache, mapper
        );

        MonthlySummaryResponse first = service.generateMonthlySummary(1L, "2026-11", false);
        assertEquals("RULE", first.source());
        assertNotNull(first.notice());
        verify(llmClient, times(2)).generate(any(), any());

        // Second non-refresh load uses per-user monthly cache (zero additional LLM calls)
        MonthlySummaryResponse cached = service.generateMonthlySummary(1L, "2026-11", false);
        assertTrue(cached.cached());
        verify(llmClient, times(2)).generate(any(), any());
    }
}
