package com.pathpilot.ai;

import com.pathpilot.ai.LlmClient.LlmResult;
import com.pathpilot.ai.dto.AiDtos.AgentChatRequest;
import com.pathpilot.ai.dto.AiDtos.AgentChatResponse;
import com.pathpilot.coding.CodingActivityRepository;
import com.pathpilot.common.ApiException;
import com.pathpilot.planner.DailyLogRepository;
import com.pathpilot.planner.LearningRecordRepository;
import com.pathpilot.planner.RecordHistoryRepository;
import com.pathpilot.planner.TaskRepository;
import com.pathpilot.project.ProjectWorklogRepository;
import com.pathpilot.user.UserProfile;
import com.pathpilot.user.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentServiceTest {

    private CodingActivityRepository codingRepo;
    private LearningRecordRepository learningRepo;
    private ProjectWorklogRepository worklogRepo;
    private TaskRepository taskRepo;
    private DailyLogRepository dailyLogRepo;
    private RecordHistoryRepository historyRepo;
    private UserProfileRepository profileRepo;
    private AiProposalRepository proposalRepo;
    private JdAnalysisService jdAnalysisService;
    private LlmClient llmClient;
    private AgentToolRegistry toolRegistry;
    private AgentService agentService;

    @BeforeEach
    void setUp() {
        codingRepo = mock(CodingActivityRepository.class);
        learningRepo = mock(LearningRecordRepository.class);
        worklogRepo = mock(ProjectWorklogRepository.class);
        taskRepo = mock(TaskRepository.class);
        dailyLogRepo = mock(DailyLogRepository.class);
        historyRepo = mock(RecordHistoryRepository.class);
        profileRepo = mock(UserProfileRepository.class);
        proposalRepo = mock(AiProposalRepository.class);
        jdAnalysisService = mock(JdAnalysisService.class);
        llmClient = mock(LlmClient.class);

        UserProfile profile = new UserProfile();
        profile.setUserId(7L);
        profile.setTimeZone("Asia/Kolkata");
        when(profileRepo.findByUserId(7L)).thenReturn(Optional.of(profile));

        AtomicLong idSeq = new AtomicLong(1);
        when(proposalRepo.save(any(AiProposal.class))).thenAnswer(inv -> {
            AiProposal p = inv.getArgument(0);
            if (p.getId() == null) p.setId(idSeq.getAndIncrement());
            return p;
        });

        ObjectMapper objectMapper = new ObjectMapper();
        AiProposalService proposalService = new AiProposalService(
                proposalRepo, taskRepo, dailyLogRepo, historyRepo, profileRepo, new AiCacheService(), objectMapper
        );
        toolRegistry = new AgentToolRegistry(
                codingRepo, learningRepo, worklogRepo, taskRepo, profileRepo, jdAnalysisService, proposalService
        );
        agentService = new AgentService(llmClient, toolRegistry, objectMapper);
    }

    @Test
    void validateToolInput_forbidsPassingUserIdArgument() {
        ApiException ex = assertThrows(ApiException.class,
                () -> toolRegistry.validateToolInput("getRecordedActivity", Map.of("userId", 99L, "range", "7d")));

        assertEquals(403, ex.getStatus().value());
        assertEquals("FORBIDDEN_TOOL_ARGUMENT", ex.getCode());

        ApiException snakeCaseEx = assertThrows(ApiException.class,
                () -> toolRegistry.validateToolInput("findOverdueTasks", Map.of("user_id", 2L)));
        assertEquals(403, snakeCaseEx.getStatus().value());
    }

    @Test
    void validateToolInput_rejectsDisallowedToolNamesAndBadRanges() {
        ApiException unknownTool = assertThrows(ApiException.class,
                () -> toolRegistry.validateToolInput("deleteAllTasks", Map.of()));
        assertEquals(400, unknownTool.getStatus().value());

        ApiException badRange = assertThrows(ApiException.class,
                () -> toolRegistry.validateToolInput("getRecordedActivity", Map.of("range", "all_time")));
        assertEquals(400, badRange.getStatus().value());
    }

    @Test
    void runAgent_enforcesMaxToolCallsAndProposeTaskNeverWritesRealTasks() {
        when(taskRepo.findAllByUserIdAndDeletedAtUtcIsNullAndTaskDateLessThanAndStatusInOrderByTaskDateAscStartTimeAscIdAsc(
                anyLong(), any(), any()))
                .thenReturn(List.of());
        when(llmClient.isConfigured()).thenReturn(true);

        String toolLoopStep = """
                {
                  "action": "TOOL_CALL",
                  "toolName": "proposeTask",
                  "arguments": {
                    "title": "Practice Binary Search",
                    "category": "DSA_PRACTICE"
                  },
                  "finalReply": null
                }
                """;
        when(llmClient.generate(any(), any()))
                .thenReturn(new LlmResult(toolLoopStep, "gemini-3.5-flash-lite", "gemini"));

        AgentChatResponse response = agentService.runAgent(7L, new AgentChatRequest("Plan my practice"));

        assertEquals(AgentService.MAX_TOOL_CALLS, response.toolCalls().size());
        verify(llmClient, times(AgentService.MAX_TOOL_CALLS)).generate(any(), any());
        assertEquals(AgentService.MAX_TOOL_CALLS, response.createdProposals().size());
        verify(taskRepo, never()).save(any());
    }

    @Test
    void runAgent_fallsBackToLocalToolsWhenLlmNotConfigured() {
        when(taskRepo.findAllByUserIdAndDeletedAtUtcIsNullAndTaskDateLessThanAndStatusInOrderByTaskDateAscStartTimeAscIdAsc(
                anyLong(), any(), any()))
                .thenReturn(List.of());
        when(codingRepo.findAllByUserIdAndDeletedAtUtcIsNullAndActivityDateBetweenOrderByActivityDateDescIdDesc(
                anyLong(), any(), any()))
                .thenReturn(List.of());
        when(learningRepo.findAllByUserIdAndRecordDateBetweenOrderByRecordDateAscIdAsc(anyLong(), any(), any()))
                .thenReturn(List.of());
        when(worklogRepo.findAllByUserIdAndDeletedAtUtcIsNullAndWorkDateBetweenOrderByWorkDateDescIdDesc(
                anyLong(), any(), any()))
                .thenReturn(List.of());
        when(taskRepo.findAllByUserIdAndDeletedAtUtcIsNullAndTaskDateBetweenOrderByTaskDateAscStartTimeAscIdAsc(
                anyLong(), any(), any()))
                .thenReturn(List.of());

        when(llmClient.isConfigured()).thenReturn(false);

        AgentChatResponse response = agentService.runAgent(7L, new AgentChatRequest("Check my week"));

        assertEquals("RULE", response.source());
        assertTrue(response.toolCalls().size() >= 2);
        verify(llmClient, never()).generate(any(), any());
    }
}
