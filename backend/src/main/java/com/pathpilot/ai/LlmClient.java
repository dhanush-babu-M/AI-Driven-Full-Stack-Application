package com.pathpilot.ai;

import java.util.Map;

/**
 * Provider-agnostic LLM client contract. Swap implementations without changing AI services.
 */
public interface LlmClient {

    boolean isConfigured();

    LlmResult generate(LlmPrompt prompt, JsonSchema schema);

    record LlmPrompt(
            String systemInstruction,
            String userMessage
    ) {
    }

    record JsonSchema(
            String name,
            Map<String, Object> schemaMap
    ) {
    }

    record LlmResult(
            String rawText,
            String model,
            String provider
    ) {
    }

    class LlmException extends RuntimeException {
        public enum Reason {
            NOT_CONFIGURED,
            RATE_LIMITED,
            QUOTA_EXHAUSTED,
            TIMEOUT,
            INVALID_RESPONSE,
            PROVIDER_ERROR
        }

        private final Reason reason;

        public LlmException(Reason reason, String message) {
            super(message);
            this.reason = reason;
        }

        public LlmException(Reason reason, String message, Throwable cause) {
            super(message, cause);
            this.reason = reason;
        }

        public Reason getReason() {
            return reason;
        }
    }
}
