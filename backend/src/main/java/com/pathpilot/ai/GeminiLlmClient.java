package com.pathpilot.ai;

import com.pathpilot.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Google Gemini API adapter (ai.google.dev) using server-side LLM_API_KEY and native JSON responseSchema.
 */
@Component
public class GeminiLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiLlmClient.class);

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public GeminiLlmClient(AppProperties appProperties, ObjectMapper objectMapper) {
        this(
                appProperties,
                objectMapper,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(appProperties.ai().timeoutMs()))
                        .build()
        );
    }

    GeminiLlmClient(AppProperties appProperties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public boolean isConfigured() {
        String key = appProperties.ai().apiKey();
        return key != null && !key.isBlank();
    }

    @Override
    public LlmResult generate(LlmPrompt prompt, JsonSchema schema) {
        if (!isConfigured()) {
            throw new LlmException(
                    LlmException.Reason.NOT_CONFIGURED,
                    "AI API key is not configured on the server."
            );
        }

        String model = appProperties.ai().model();
        String baseUrl = appProperties.ai().baseUrl().replaceAll("/+$", "");
        String endpoint = baseUrl + "/models/" + model + ":generateContent";
        int maxRetries = appProperties.ai().maxRetries();
        Duration timeout = Duration.ofMillis(appProperties.ai().timeoutMs());

        String requestBody = buildRequestJson(prompt, schema);

        LlmException lastFailure = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            if (attempt > 0) {
                sleepBackoff(attempt);
            }
            try {
                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .timeout(timeout)
                        .header("Content-Type", "application/json")
                        .header("x-goog-api-key", appProperties.ai().apiKey())
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();

                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();

                if (status == 200) {
                    String text = extractCandidateText(response.body());
                    return new LlmResult(text, model, "gemini");
                }
                if (status == 429) {
                    boolean quota = response.body() != null && response.body().toLowerCase().contains("quota");
                    lastFailure = new LlmException(
                            quota ? LlmException.Reason.QUOTA_EXHAUSTED : LlmException.Reason.RATE_LIMITED,
                            "AI provider rate limit or quota reached."
                    );
                    if (attempt < maxRetries && !quota) {
                        continue;
                    }
                    throw lastFailure;
                }
                if (status >= 500) {
                    lastFailure = new LlmException(
                            LlmException.Reason.PROVIDER_ERROR,
                            "AI provider returned status " + status + "."
                    );
                    continue;
                }

                log.warn("Gemini API returned non-retryable status {} for model {}", status, model);
                throw new LlmException(
                        LlmException.Reason.PROVIDER_ERROR,
                        "AI provider rejected the request (status " + status + ")."
                );
            } catch (HttpTimeoutException ex) {
                lastFailure = new LlmException(LlmException.Reason.TIMEOUT, "AI request timed out.", ex);
            } catch (IOException ex) {
                lastFailure = new LlmException(LlmException.Reason.PROVIDER_ERROR, "Network error calling AI provider.", ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new LlmException(LlmException.Reason.PROVIDER_ERROR, "AI request interrupted.", ex);
            }
        }

        throw lastFailure != null
                ? lastFailure
                : new LlmException(LlmException.Reason.PROVIDER_ERROR, "AI request failed.");
    }

    private String buildRequestJson(LlmPrompt prompt, JsonSchema schema) {
        Map<String, Object> payload = new LinkedHashMap<>();

        if (prompt.systemInstruction() != null && !prompt.systemInstruction().isBlank()) {
            payload.put("systemInstruction", Map.of(
                    "parts", List.of(Map.of("text", prompt.systemInstruction()))
            ));
        }

        payload.put("contents", List.of(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", prompt.userMessage()))
        )));

        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("temperature", 0.2);
        if (schema != null && schema.schemaMap() != null && !schema.schemaMap().isEmpty()) {
            generationConfig.put("responseMimeType", "application/json");
            generationConfig.put("responseSchema", schema.schemaMap());
        }
        payload.put("generationConfig", generationConfig);

        return objectMapper.writeValueAsString(payload);
    }

    private String extractCandidateText(String body) {
        if (body == null || body.isBlank()) {
            throw new LlmException(LlmException.Reason.INVALID_RESPONSE, "Empty AI response.");
        }
        JsonNode root = objectMapper.readTree(body);
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            throw new LlmException(LlmException.Reason.INVALID_RESPONSE, "AI response contained no candidates.");
        }
        JsonNode parts = candidates.get(0).path("content").path("parts");
        if (!parts.isArray() || parts.isEmpty()) {
            throw new LlmException(LlmException.Reason.INVALID_RESPONSE, "AI response contained no content parts.");
        }
        String text = parts.get(0).path("text").asText("");
        if (text.isBlank()) {
            throw new LlmException(LlmException.Reason.INVALID_RESPONSE, "AI response text was blank.");
        }
        return text.trim();
    }

    private void sleepBackoff(int attempt) {
        try {
            long delayMs = 400L * (1L << (attempt - 1));
            Thread.sleep(Math.min(delayMs, 2000L));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
