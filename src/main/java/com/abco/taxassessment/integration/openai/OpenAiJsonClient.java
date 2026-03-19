package com.abco.taxassessment.integration.openai;

import com.abco.taxassessment.config.properties.AppProperties;
import com.abco.taxassessment.exception.OpenAiRateLimitException;
import com.abco.taxassessment.exception.OpenAiSchemaViolationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Reusable OpenAI client with structured output (JSON Schema strict mode) (§Implementation §3).
 *
 * Design decisions:
 * 1. Uses java.net.http.HttpClient rather than the OpenAI Java SDK for precise control
 *    over request/response handling, retry semantics, and schema validation.
 * 2. Structured output uses response_format with type="json_schema" and strict=true.
 *    This guarantees the response matches the provided JSON Schema — no heuristic repair.
 * 3. Resilience4j composition: Retry → CircuitBreaker → TimeLimiter (§11.4).
 * 4. Token usage is tracked per operation via Micrometer counters.
 * 5. 429 responses throw OpenAiRateLimitException (retryable).
 * 6. 400/401/422 responses throw OpenAiSchemaViolationException (non-retryable).
 * 7. Schema violations in the parsed response throw OpenAiSchemaViolationException (non-retryable).
 *
 * Non-goals: heuristic JSON repair on malformed responses — not implemented per spec.
 */
@Component
public class OpenAiJsonClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiJsonClient.class);
    private static final String OPENAI_CHAT_URL = "https://api.openai.com/v1/chat/completions";

    private final AppProperties.OpenAiProperties config;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final HttpClient httpClient;

    public OpenAiJsonClient(AppProperties appProperties,
                             ObjectMapper objectMapper,
                             MeterRegistry meterRegistry) {
        this.config = appProperties.openai();
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Calls OpenAI with a system prompt, user content, and a JSON Schema definition.
     * Returns the parsed response as a typed object.
     *
     * Resilience4j composition order: Retry → CircuitBreaker → TimeLimiter (§11.4).
     *
     * @param operation  Name used for metrics and logs (e.g., "transaction-categorization")
     * @param systemPrompt  System context for the model
     * @param userContent   The content to process
     * @param jsonSchema    JSON Schema (strict) defining the required response shape
     * @param responseType  The Java type to deserialize the response into
     */
    @Retry(name = "openai-api")
    @CircuitBreaker(name = "openai-api")
    @TimeLimiter(name = "openai-api")
    public <T> CompletableFuture<T> callWithStructuredOutput(String operation,
                                                              String systemPrompt,
                                                              String userContent,
                                                              Map<String, Object> jsonSchema,
                                                              Class<T> responseType) {
        return CompletableFuture.supplyAsync(() -> {
            Timer.Sample sample = Timer.start(meterRegistry);
            try {
                String requestBody = buildRequestBody(systemPrompt, userContent, jsonSchema);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(OPENAI_CHAT_URL))
                        .header("Authorization", "Bearer " + config.apiKey())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();

                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                handleHttpErrors(operation, response);

                T result = parseStructuredResponse(operation, response.body(), responseType);

                recordTokenUsage(operation, response.body());

                sample.stop(meterRegistry.timer("openai.request.duration",
                        "operation", operation, "status", "success"));

                return result;

            } catch (OpenAiRateLimitException | OpenAiSchemaViolationException e) {
                sample.stop(meterRegistry.timer("openai.request.duration",
                        "operation", operation, "status", "error"));
                throw e;
            } catch (Exception e) {
                sample.stop(meterRegistry.timer("openai.request.duration",
                        "operation", operation, "status", "error"));
                throw new RuntimeException("OpenAI request failed for operation: " + operation, e);
            }
        });
    }

    private void handleHttpErrors(String operation, HttpResponse<String> response) {
        int statusCode = response.statusCode();

        if (statusCode == 429) {
            long retryAfter = response.headers()
                    .firstValueAsLong("Retry-After")
                    .orElse(60L);
            meterRegistry.counter("openai.rate_limit", "operation", operation).increment();
            throw new OpenAiRateLimitException(retryAfter);
        }

        if (statusCode == 400 || statusCode == 401 || statusCode == 422) {
            log.error("Non-retryable OpenAI error [{}] for operation {}: {}",
                      statusCode, operation, response.body());
            throw new OpenAiSchemaViolationException(operation,
                    "HTTP " + statusCode + ": " + response.body());
        }

        if (statusCode >= 500) {
            log.warn("Retryable OpenAI server error [{}] for operation {}", statusCode, operation);
            throw new RuntimeException("OpenAI server error: " + statusCode);
        }
    }

    private <T> T parseStructuredResponse(String operation, String responseBody, Class<T> responseType) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode content = root.path("choices").get(0)
                                   .path("message")
                                   .path("content");

            if (content.isMissingNode() || content.isNull()) {
                throw new OpenAiSchemaViolationException(operation, "Response content is null or missing");
            }

            String contentStr = content.asText();

            // Parse the JSON content string into the target type
            T result = objectMapper.readValue(contentStr, responseType);

            if (result == null) {
                throw new OpenAiSchemaViolationException(operation, "Parsed response is null");
            }

            log.debug("Successfully parsed OpenAI response for operation: {}", operation);
            return result;

        } catch (JsonProcessingException e) {
            throw new OpenAiSchemaViolationException(operation,
                    "JSON parsing failed: " + e.getMessage());
        }
    }

    private void recordTokenUsage(String operation, String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode usage = root.path("usage");
            if (!usage.isMissingNode()) {
                int promptTokens = usage.path("prompt_tokens").asInt(0);
                int completionTokens = usage.path("completion_tokens").asInt(0);
                int totalTokens = usage.path("total_tokens").asInt(0);

                meterRegistry.counter("openai.tokens.prompt", "operation", operation)
                              .increment(promptTokens);
                meterRegistry.counter("openai.tokens.completion", "operation", operation)
                              .increment(completionTokens);
                meterRegistry.counter("openai.tokens.total", "operation", operation)
                              .increment(totalTokens);
            }
        } catch (Exception e) {
            log.warn("Could not parse token usage from OpenAI response: {}", e.getMessage());
        }
    }

    private String buildRequestBody(String systemPrompt, String userContent,
                                     Map<String, Object> jsonSchema) throws JsonProcessingException {
        Map<String, Object> responseFormat = Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                        "name", "structured_response",
                        "strict", true,
                        "schema", jsonSchema
                )
        );

        Map<String, Object> requestBody = Map.of(
                "model", config.model(),
                "max_tokens", config.maxTokens(),
                "temperature", config.temperature(),
                "messages", java.util.List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userContent)
                ),
                "response_format", responseFormat
        );

        return objectMapper.writeValueAsString(requestBody);
    }
}
