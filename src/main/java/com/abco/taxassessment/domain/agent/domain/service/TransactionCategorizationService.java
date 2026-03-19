package com.abco.taxassessment.domain.agent.domain.service;

import com.abco.taxassessment.domain.agent.domain.entity.AgentJobEntity;
import com.abco.taxassessment.domain.agent.repository.AgentJobRepository;
import com.abco.taxassessment.domain.agent.repository.ProcessedEventRepository;
import com.abco.taxassessment.domain.transaction.domain.entity.TransactionEntity;
import com.abco.taxassessment.domain.transaction.repository.TransactionRepository;
import com.abco.taxassessment.event.domain.BaseAgentEvent;
import com.abco.taxassessment.integration.openai.OpenAiJsonClient;
import com.abco.taxassessment.tenant.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

/**
 * UC-18: Transaction Categorization Agent.
 *
 * Classifies each transaction into the two-level tax category hierarchy.
 * Priority: tenant custom rules first, then AI inference via OpenAI structured output.
 *
 * Input event: transactions.parsed (TransactionsParsedEvent)
 * Output event: transactions.categorized (TransactionsCategorizatedEvent)
 * Failure: retried up to maxRetryAttempts; then DLQ-routed.
 */
@Service
@Transactional
public class TransactionCategorizationService {

    private static final String CATEGORIZATION_SYSTEM_PROMPT = """
        You are a US tax categorization expert for small businesses.
        Classify each bank transaction into the appropriate tax category.

        Available categories:
        REVENUE_SALES, REVENUE_SERVICES, REVENUE_1099_NEC, REVENUE_RENTAL, REVENUE_INTEREST,
        EXPENSE_ADVERTISING, EXPENSE_MEALS, EXPENSE_OFFICE_RENT, EXPENSE_HOME_OFFICE,
        EXPENSE_TRAVEL, EXPENSE_PAYROLL, EXPENSE_CONTRACTOR, EXPENSE_SOFTWARE,
        EXPENSE_UTILITIES, EXPENSE_INSURANCE, EXPENSE_LEGAL_PROFESSIONAL,
        EXPENSE_OFFICE_SUPPLIES, EXPENSE_DEPRECIATION, EXPENSE_INTEREST, EXPENSE_TAXES_LICENSES,
        EXPENSE_MARKETING, EXPENSE_BANK_FEES, EXPENSE_HEALTH_INSURANCE, EXPENSE_RETIREMENT,
        TRANSFER, OWNER_DRAW, EXPENSE_UNCATEGORIZED.

        Rules:
        - Meals (restaurants, food delivery for business): EXPENSE_MEALS (50% deductible)
        - Software subscriptions (AWS, GitHub, Slack, Zoom): EXPENSE_SOFTWARE
        - Payroll processors (Gusto, ADP): EXPENSE_PAYROLL
        - Payment processors (Stripe, Square, PayPal credits): REVENUE_SALES
        - Freelancer platforms (Upwork, Fiverr payments out): EXPENSE_CONTRACTOR
        - Inbound wire transfers with no business description: requires_review=true
        - Round numbers ≥$10,000: flag regulatory_flag=true (potential CTR)

        Respond with confidence scores between 0.0 and 1.0.
        """;

    private final TransactionRepository transactionRepository;
    private final OpenAiJsonClient openAiJsonClient;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    public TransactionCategorizationService(TransactionRepository transactionRepository,
                                             OpenAiJsonClient openAiJsonClient,
                                             ObjectMapper objectMapper,
                                             KafkaTemplate<String, Object> kafkaTemplate,
                                             MeterRegistry meterRegistry) {
        this.transactionRepository = transactionRepository;
        this.openAiJsonClient = openAiJsonClient;
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Categorizes all transactions for a statement.
     * Called by the Kafka consumer after parsing is complete.
     */
    public void categorizeTransactionsForStatement(UUID tenantId, UUID statementId) {
        List<TransactionEntity> transactions = transactionRepository
                .findAllByTenantIdAndStatementId(tenantId, statementId);

        if (transactions.isEmpty()) {
            return;
        }

        String userContent = buildCategorizationRequest(transactions);

        try {
            CategorizationResponse response = openAiJsonClient.callWithStructuredOutput(
                    "transaction-categorization",
                    CATEGORIZATION_SYSTEM_PROMPT,
                    userContent,
                    buildJsonSchema(),
                    CategorizationResponse.class
            ).get();

            applyCategorizationResults(transactions, response);

        } catch (ExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Categorization failed for statement " + statementId, e);
        }
    }

    private void applyCategorizationResults(List<TransactionEntity> transactions,
                                             CategorizationResponse response) {
        for (TransactionEntity transaction : transactions) {
            CategorizationResult result = response.results().stream()
                    .filter(r -> r.transactionId().equals(transaction.getId().toString()))
                    .findFirst()
                    .orElse(null);

            if (result != null) {
                transaction.applyCategory(result.categoryCode(),
                                          BigDecimal.valueOf(result.confidence()));
                if (result.requiresReview()) {
                    transaction.flagForReview(result.reviewReason());
                }
                transactionRepository.save(transaction);
            }
        }
    }

    private String buildCategorizationRequest(List<TransactionEntity> transactions) {
        try {
            return objectMapper.writeValueAsString(
                transactions.stream().map(t -> Map.of(
                    "transactionId", t.getId().toString(),
                    "date", t.getTransactionDate().toString(),
                    "description", t.getDescription(),
                    "amount", t.getAmount().toString(),
                    "sign", t.getSign().name()
                )).toList()
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize categorization request", e);
        }
    }

    private Map<String, Object> buildJsonSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("results"),
            "additionalProperties", false,
            "properties", Map.of(
                "results", Map.of(
                    "type", "array",
                    "items", Map.of(
                        "type", "object",
                        "required", List.of("transactionId", "categoryCode", "confidence",
                                            "requiresReview", "reviewReason"),
                        "additionalProperties", false,
                        "properties", Map.of(
                            "transactionId", Map.of("type", "string"),
                            "categoryCode", Map.of("type", "string"),
                            "confidence", Map.of("type", "number", "minimum", 0, "maximum", 1),
                            "requiresReview", Map.of("type", "boolean"),
                            "reviewReason", Map.of("type", "string")
                        )
                    )
                )
            )
        );
    }

    public record CategorizationResponse(List<CategorizationResult> results) {}

    public record CategorizationResult(
            String transactionId,
            String categoryCode,
            double confidence,
            boolean requiresReview,
            String reviewReason
    ) {}
}
