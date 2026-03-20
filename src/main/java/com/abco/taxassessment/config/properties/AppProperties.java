package com.abco.taxassessment.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Strongly-typed, validated application configuration (§13.1).
 *
 * Uses Java records for immutability. All fields are validated at startup
 * via @Validated — misconfiguration fails fast rather than at runtime.
 */
@ConfigurationProperties(prefix = "app")
@Validated
public record AppProperties(
        @NotBlank String environment,
        @NotNull @Valid KafkaProperties kafka,
        @NotNull @Valid OpenAiProperties openai,
        @NotNull @Valid NotificationProperties notification,
        @NotNull @Valid OutboxProperties outbox,
        @NotNull @Valid AgentProperties agent,
        @NotNull @Valid FxRateProperties fxRate
) {

    public record KafkaProperties(
            @NotNull @Valid TopicProperties topics,
            @NotNull @Valid ConsumerGroupProperties consumerGroups,
            @Min(1) int replicationFactor
    ) {}

    public record TopicProperties(
            @NotBlank String statementIngested,
            @NotBlank String documentClassified,
            @NotBlank String textExtracted,
            @NotBlank String transactionsParsed,
            @NotBlank String transactionsCategorized,
            @NotBlank String taxRulesApplied,
            @NotBlank String reconciliationComplete,
            @NotBlank String assessmentAggregated,
            @NotBlank String anomaliesDetected,
            @NotBlank String assessmentFinalized,
            @NotBlank String dlqPrefix
    ) {}

    public record ConsumerGroupProperties(
            @NotBlank String documentClassification,
            @NotBlank String ocrExtraction,
            @NotBlank String transactionParsing,
            @NotBlank String transactionCategorization,
            @NotBlank String taxRuleApplication,
            @NotBlank String reconciliation,
            @NotBlank String assessmentAggregation,
            @NotBlank String anomalyDetection,
            @NotBlank String assessmentFinalization,
            @NotBlank String notificationDispatch
    ) {}

    public record OpenAiProperties(
            @NotBlank String apiKey,
            @NotBlank String model,
            @Min(1) int maxTokens,
            double temperature
    ) {}

    public record NotificationProperties(
            @NotBlank String fromEmail,
            @NotBlank String fromName,
            @NotNull @Valid TwilioProperties twilio
    ) {}

    public record TwilioProperties(
            @NotBlank String accountSid,
            @NotBlank String authToken,
            @NotBlank String fromNumber
    ) {}

    public record OutboxProperties(
            @Min(100) long pollingIntervalMs,
            @Min(1) int batchSize
    ) {}

    public record AgentProperties(
            double confidenceThreshold,
            @Min(1) int maxRetryAttempts
    ) {}

    public record FxRateProperties(
            @NotBlank String baseCurrency
    ) {}
}
