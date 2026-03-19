package com.abco.taxassessment.config;

import com.abco.taxassessment.config.properties.AppProperties;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

/**
 * Kafka topic definitions for the agent pipeline (§9).
 *
 * Each agent stage has a dedicated input topic and a DLQ (dead-letter queue).
 * Topics are auto-created by KafkaAdmin if they don't exist — safe for idempotent runs.
 * In production, topic creation is controlled by GitOps; this serves as documentation
 * and fallback for local dev and integration tests.
 *
 * Partition count = 12 (balanced for 3 consumer instances × 4 partitions each).
 * Replication factor = 3 (standard for production Kafka clusters).
 */
@Configuration
public class KafkaConfig {

    private final AppProperties.KafkaProperties kafka;

    public KafkaConfig(AppProperties appProperties) {
        this.kafka = appProperties.kafka();
    }

    // ===== Agent Pipeline Topics =====

    @Bean
    public NewTopic statementIngestedTopic() {
        return TopicBuilder.name(kafka.topics().statementIngested())
                .partitions(12).replicas(3).build();
    }

    @Bean
    public NewTopic documentClassifiedTopic() {
        return TopicBuilder.name(kafka.topics().documentClassified())
                .partitions(12).replicas(3).build();
    }

    @Bean
    public NewTopic textExtractedTopic() {
        return TopicBuilder.name(kafka.topics().textExtracted())
                .partitions(12).replicas(3).build();
    }

    @Bean
    public NewTopic transactionsParsedTopic() {
        return TopicBuilder.name(kafka.topics().transactionsParsed())
                .partitions(12).replicas(3).build();
    }

    @Bean
    public NewTopic transactionsCategorizeTopic() {
        return TopicBuilder.name(kafka.topics().transactionsCategorized())
                .partitions(12).replicas(3).build();
    }

    @Bean
    public NewTopic taxRulesAppliedTopic() {
        return TopicBuilder.name(kafka.topics().taxRulesApplied())
                .partitions(12).replicas(3).build();
    }

    @Bean
    public NewTopic reconciliationCompleteTopic() {
        return TopicBuilder.name(kafka.topics().reconciliationComplete())
                .partitions(12).replicas(3).build();
    }

    @Bean
    public NewTopic assessmentAggregatedTopic() {
        return TopicBuilder.name(kafka.topics().assessmentAggregated())
                .partitions(12).replicas(3).build();
    }

    @Bean
    public NewTopic anomaliesDetectedTopic() {
        return TopicBuilder.name(kafka.topics().anomaliesDetected())
                .partitions(12).replicas(3).build();
    }

    @Bean
    public NewTopic assessmentFinalizedTopic() {
        return TopicBuilder.name(kafka.topics().assessmentFinalized())
                .partitions(12).replicas(3).build();
    }

    // ===== DLQ Topics =====

    @Bean
    public NewTopic statementIngestedDlq() {
        return TopicBuilder.name(kafka.topics().dlqPrefix() + "." + kafka.topics().statementIngested())
                .partitions(3).replicas(3).build();
    }

    @Bean
    public NewTopic documentClassifiedDlq() {
        return TopicBuilder.name(kafka.topics().dlqPrefix() + "." + kafka.topics().documentClassified())
                .partitions(3).replicas(3).build();
    }

    @Bean
    public NewTopic textExtractedDlq() {
        return TopicBuilder.name(kafka.topics().dlqPrefix() + "." + kafka.topics().textExtracted())
                .partitions(3).replicas(3).build();
    }

    @Bean
    public NewTopic transactionsParsedDlq() {
        return TopicBuilder.name(kafka.topics().dlqPrefix() + "." + kafka.topics().transactionsParsed())
                .partitions(3).replicas(3).build();
    }

    @Bean
    public NewTopic transactionsCategorizedDlq() {
        return TopicBuilder.name(kafka.topics().dlqPrefix() + "." + kafka.topics().transactionsCategorized())
                .partitions(3).replicas(3).build();
    }

    @Bean
    public NewTopic taxRulesAppliedDlq() {
        return TopicBuilder.name(kafka.topics().dlqPrefix() + "." + kafka.topics().taxRulesApplied())
                .partitions(3).replicas(3).build();
    }

    @Bean
    public NewTopic reconciliationCompleteDlq() {
        return TopicBuilder.name(kafka.topics().dlqPrefix() + "." + kafka.topics().reconciliationComplete())
                .partitions(3).replicas(3).build();
    }

    @Bean
    public NewTopic assessmentAggregatedDlq() {
        return TopicBuilder.name(kafka.topics().dlqPrefix() + "." + kafka.topics().assessmentAggregated())
                .partitions(3).replicas(3).build();
    }
}
