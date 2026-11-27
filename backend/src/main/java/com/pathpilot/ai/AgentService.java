package com.pathpilot.ai;

import com.pathpilot.ai.AgentToolRegistry.ToolExecutionResult;
import com.pathpilot.ai.LlmClient.JsonSchema;
import com.pathpilot.ai.LlmClient.LlmException;
import com.pathpilot.ai.LlmClient.LlmPrompt;
import com.pathpilot.ai.LlmClient.LlmResult;
import com.pathpilot.ai.dto.AiDtos;
import com.pathpilot.ai.dto.AiDtos.AgentChatRequest;
import com.pathpilot.ai.dto.AiDtos.AgentChatResponse;
import com.pathpilot.ai.dto.AiDtos.AgentToolTraceDto;
import com.pathpilot.ai.dto.AiDtos.AiProposalDto;
import com.pathpilot.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Bounded tool-calling agent loop (max 4 tool calls per request).
 * Never passes userId through the LLM; always enforces CurrentUser.id().
 */
@Service
public class AgentService {

    public static final int MAX_TOOL_CALLS = 4;
    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final LlmClient llmClient;
    private final AgentToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    public AgentService(LlmClient llmClient, AgentToolRegistry toolRegistry, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AgentChatResponse runAgent(Long userId, AgentChatRequest request) {
        List<AgentToolTraceDto> traces = new ArrayList<>();
        List<AiProposalDto> proposals = new ArrayList<>();

        if (!llmClient.isConfigured()) {
            return runDeterministicFallback(
                    userId,
                    "AI API key is not configured — ran local read-only career tools instead.",
                    traces,
                    proposals
            );
        }

        StringBuilder scratchpad = new StringBuilder();
        scratchpad.append("User request: ").append(request.message().trim()).append("\n");

        JsonSchema schema = buildAgentStepSchema();
        String systemPrompt = """
                You are PathPilot's bounded Career Agent.
                Available tools (you may call at most 4 total):
                1. getRecordedActivity(range): range in ["today","7d","week","30d","month"]
                2. calculateProgress(range): range in ["today","week","month"]
                3. findOverdueTasks(): no arguments
                4. analyzeJobDescription(text): plain-text JD
                5. proposeTask(title, date, category): creates a PENDING ai_proposal ONLY (never a real task). Allowed categories: JAVA_LEARNING, SPRING_BOOT, SQL_MYSQL, REACT_FRONTEND, CODING_PRACTICE, PROJECT_DEVELOPMENT, JOB_APPLICATIONS, INTERVIEW_PREP, MEALS_PERSONAL, OTHER.

                STRICT SECURITY & HONESTY RULES:
                - NEVER pass userId/user_id in tool arguments.
                - Use ONLY numbers returned by tools; never invent achievements.
                - Set action="TOOL_CALL" to call one tool, or action="FINAL_ANSWER" when ready to answer.
                """;

        for (int step = 0; step < MAX_TOOL_CALLS; step++) {
            try {
                LlmResult res = llmClient.generate(new LlmPrompt(systemPrompt, scratchpad.toString()), schema);
                AgentStepDecision decision = objectMapper.readValue(stripFences(res.rawText()), AgentStepDecision.class);

                if ("FINAL_ANSWER".equalsIgnoreCase(decision.action())
                        && decision.finalReply() != null && !decision.finalReply().isBlank()) {
                    return new AgentChatResponse(
                            decision.finalReply().trim(),
                            "AI",
                            AiDtos.AI_DISCLAIMER,
                            null,
                            traces,
                            proposals
                    );
                }

                if ("TOOL_CALL".equalsIgnoreCase(decision.action()) && decision.toolName() != null) {
                    Map<String, Object> args = decision.arguments() != null ? decision.arguments() : Map.of();
                    ToolExecutionResult toolResult = toolRegistry.execute(userId, decision.toolName(), args);
                    traces.add(new AgentToolTraceDto(decision.toolName(), args, toolResult.summary()));
                    if (toolResult.createdProposal() != null) {
                        proposals.add(toolResult.createdProposal());
                    }
                    scratchpad.append("\nTool ").append(decision.toolName())
                            .append(" result: ").append(objectMapper.writeValueAsString(toolResult.data())).append("\n");
                } else {
                    break;
                }
            } catch (ApiException apiEx) {
                throw apiEx;
            } catch (LlmException llmEx) {
                log.warn("Agent LLM failed ({}): {}", llmEx.getReason(), llmEx.getMessage());
                return runDeterministicFallback(
                        userId,
                        "AI provider unavailable (" + llmEx.getReason() + ") — showing rule-based summary from your records.",
                        traces,
                        proposals
                );
            } catch (Exception ex) {
                log.debug("Agent step parse error: {}", ex.getMessage());
                break;
            }
        }

        // If loop reached MAX_TOOL_CALLS without FINAL_ANSWER, request one final answer without calling tools.
        if (!traces.isEmpty()) {
            try {
                LlmResult finalRes = llmClient.generate(
                        new LlmPrompt(
                                systemPrompt + "\nTool budget reached. Respond ONLY with action=\"FINAL_ANSWER\".",
                                scratchpad.toString()
                        ),
                        schema
                );
                AgentStepDecision decision = objectMapper.readValue(stripFences(finalRes.rawText()), AgentStepDecision.class);
                if (decision.finalReply() != null && !decision.finalReply().isBlank()) {
                    return new AgentChatResponse(
                            decision.finalReply().trim(),
                            "AI",
                            AiDtos.AI_DISCLAIMER,
                            null,
                            traces,
                            proposals
                    );
                }
            } catch (Exception ignored) {
            }
        }

        return runDeterministicFallback(
                userId,
                "Could not complete multi-step AI response — showing factual summary from your records.",
                traces,
                proposals
        );
    }

    private AgentChatResponse runDeterministicFallback(
            Long userId,
            String notice,
            List<AgentToolTraceDto> traces,
            List<AiProposalDto> proposals
    ) {
        if (traces.isEmpty()) {
            ToolExecutionResult activity = toolRegistry.execute(userId, "getRecordedActivity", Map.of("range", "7d"));
            traces.add(new AgentToolTraceDto("getRecordedActivity", Map.of("range", "7d"), activity.summary()));
            ToolExecutionResult progress = toolRegistry.execute(userId, "calculateProgress", Map.of("range", "today"));
            traces.add(new AgentToolTraceDto("calculateProgress", Map.of("range", "today"), progress.summary()));
            ToolExecutionResult overdue = toolRegistry.execute(userId, "findOverdueTasks", Map.of());
            traces.add(new AgentToolTraceDto("findOverdueTasks", Map.of(), overdue.summary()));
        }
        String reply = traces.stream()
                .map(AgentToolTraceDto::summary)
                .reduce((a, b) -> a + " " + b)
                .orElse("No activity recorded yet.");
        return new AgentChatResponse(reply, "RULE", "Generated from your saved records.", notice, traces, proposals);
    }

    private JsonSchema buildAgentStepSchema() {
        Map<String, Object> root = Map.of(
                "type", "object",
                "properties", Map.of(
                        "action", Map.of("type", "string"),
                        "toolName", Map.of("type", "string"),
                        "arguments", Map.of("type", "object"),
                        "finalReply", Map.of("type", "string")
                ),
                "required", List.of("action")
        );
        return new JsonSchema("agent_step", root);
    }

    private String stripFences(String raw) {
        if (raw == null) return "";
        String t = raw.trim();
        if (t.startsWith("```")) {
            int idx = t.indexOf('\n');
            if (idx > 0) t = t.substring(idx + 1);
            if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
        }
        return t.trim();
    }

    public record AgentStepDecision(
            String action,
            String toolName,
            Map<String, Object> arguments,
            String finalReply
    ) {
    }
}
