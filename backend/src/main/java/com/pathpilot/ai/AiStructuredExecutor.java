package com.pathpilot.ai;

import com.pathpilot.ai.LlmClient.JsonSchema;
import com.pathpilot.ai.LlmClient.LlmException;
import com.pathpilot.ai.LlmClient.LlmPrompt;
import com.pathpilot.ai.LlmClient.LlmResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Runs structured LLM calls with schema/domain validation, a single corrective retry,
 * and automatic deterministic fallback on missing key, quota exhaustion, or invalid JSON.
 */
@Component
public class AiStructuredExecutor {

    private static final Logger log = LoggerFactory.getLogger(AiStructuredExecutor.class);

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public AiStructuredExecutor(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    public <T> ExecutionOutcome<T> execute(
            LlmPrompt prompt,
            JsonSchema schema,
            Class<T> targetType,
            ResponseValidator<T> validator
    ) {
        if (!llmClient.isConfigured()) {
            return ExecutionOutcome.fallback(
                    "AI API key is not configured on the server — showing rule-based output."
            );
        }

        String correctionNote = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            LlmPrompt effectivePrompt = correctionNote == null
                    ? prompt
                    : new LlmPrompt(
                            prompt.systemInstruction(),
                            prompt.userMessage() + "\n\nCORRECTION REQUIRED (previous output was rejected): " + correctionNote
                    );
            try {
                LlmResult result = llmClient.generate(effectivePrompt, schema);
                String cleanedJson = stripMarkdownFences(result.rawText());
                T parsed = objectMapper.readValue(cleanedJson, targetType);
                String validationError = validator != null ? validator.validate(parsed) : null;
                if (validationError == null) {
                    return ExecutionOutcome.ai(parsed);
                }
                correctionNote = validationError;
                log.debug("AI output failed validation on attempt {}: {}", attempt + 1, validationError);
            } catch (LlmException ex) {
                log.warn("AI provider call failed ({}): {}", ex.getReason(), ex.getMessage());
                return ExecutionOutcome.fallback(toFriendlyNotice(ex));
            } catch (Exception parseEx) {
                correctionNote = "Response must be valid JSON strictly matching the requested schema.";
                log.debug("AI JSON parse failed on attempt {}: {}", attempt + 1, parseEx.getMessage());
            }
        }

        return ExecutionOutcome.fallback(
                "AI output did not pass grounding/schema validation — showing rule-based output."
        );
    }

    private String stripMarkdownFences(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }
        return trimmed.trim();
    }

    private String toFriendlyNotice(LlmException ex) {
        return switch (ex.getReason()) {
            case NOT_CONFIGURED -> "AI API key is not configured on the server — showing rule-based output.";
            case RATE_LIMITED -> "AI rate limit reached — showing rule-based output for now.";
            case QUOTA_EXHAUSTED -> "AI quota exhausted — showing rule-based output for now.";
            case TIMEOUT -> "AI request timed out — showing rule-based output.";
            default -> "AI assistant is temporarily unavailable — showing rule-based output.";
        };
    }

    @FunctionalInterface
    public interface ResponseValidator<T> {
        /**
         * @return null if valid, or a short correction message if invalid.
         */
        String validate(T value);
    }

    public record ExecutionOutcome<T>(
            boolean success,
            T data,
            String notice
    ) {
        public static <T> ExecutionOutcome<T> ai(T data) {
            return new ExecutionOutcome<>(true, data, null);
        }

        public static <T> ExecutionOutcome<T> fallback(String notice) {
            return new ExecutionOutcome<>(false, null, notice);
        }
    }
}
