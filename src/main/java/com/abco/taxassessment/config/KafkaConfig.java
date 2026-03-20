package com.abco.taxassessment.config;

import com.abco.taxassessment.config.properties.AppProperties;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

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
    private final KafkaProperties kafkaProperties;

    public KafkaConfig(AppProperties appProperties, KafkaProperties kafkaProperties) {
        this.kafka = appProperties.kafka();
        this.kafkaProperties = kafkaProperties;
    }

    // ===== Primary KafkaTemplate (JsonSerializer) =====
    //
    // Explicitly defined because defining outboxKafkaTemplate (a KafkaTemplate<String,String>)
    // satisfies Spring Boot's @ConditionalOnMissingBean(KafkaTemplate.class), preventing
    // auto-configuration of the standard KafkaTemplate. Agent services (AbstractAgentService,
    // TransactionCategorizationService) inject this unqualified bean.
    //
    // Spring Boot auto-configures ProducerFactory<?,?> via KafkaAutoConfiguration since we do
    // not define our own ProducerFactory bean; we inject it here by its wildcard type and cast.

    @Bean
    @Primary
    @SuppressWarnings({"unchecked", "rawtypes"})
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<?, ?> kafkaProducerFactory) {
        return new KafkaTemplate<>((ProducerFactory<String, Object>) kafkaProducerFactory);
    }

    // ===== Outbox Producer (StringSerializer) =====
    //
    // The outbox pattern stores event payloads as pre-serialized JSON strings in the DB.
    // Using JsonSerializer here would double-encode the String (wrapping it in extra quotes),
    // making it impossible for the consumer to deserialize to StatementIngestedEvent.
    // StringSerializer sends the raw UTF-8 JSON bytes — exactly what the consumer expects.

    @Bean("outboxProducerFactory")
    public ProducerFactory<String, String> outboxProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean("outboxKafkaTemplate")
    public KafkaTemplate<String, String> outboxKafkaTemplate(
            @Qualifier("outboxProducerFactory") ProducerFactory<String, String> outboxProducerFactory) {
        return new KafkaTemplate<>(outboxProducerFactory);
    }

    // ===== Listener Container Factory =====
    //
    // Overrides Spring Boot's auto-configured factory to add:
    // - ExponentialBackOff (1s → 2s → 4s, max 3 attempts) before DLQ routing
    // - DeadLetterPublishingRecoverer: routes poison-pill messages to dlq.<topic>
    // - MANUAL_IMMEDIATE ack mode (consumers ack only after successful processing)
    //
    // The ErrorHandlingDeserializer configured in application.yaml ensures that
    // deserialization failures are delivered to this error handler (as a header-enriched
    // null-value record) rather than crashing the consumer thread.

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Bean
    public ConcurrentKafkaListenerContainerFactory<?, ?> kafkaListenerContainerFactory(
            ConsumerFactory<?, ?> consumerFactory,
            @Qualifier("kafkaTemplate") KafkaTemplate<String, Object> kafkaTemplate) {

        ConcurrentKafkaListenerContainerFactory factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (ConsumerRecord<?, ?> record, Exception ex) -> new TopicPartition(
                        kafka.topics().dlqPrefix() + "." + record.topic(), 0));

        ExponentialBackOff backOff = new ExponentialBackOff(1_000L, 2.0);
        backOff.setMaxAttempts(3);

        factory.setCommonErrorHandler(new DefaultErrorHandler(recoverer, backOff));
        return factory;
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
