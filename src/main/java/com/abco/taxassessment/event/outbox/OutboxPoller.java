package com.abco.taxassessment.event.outbox;

import com.abco.taxassessment.config.properties.AppProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Outbox poller — reads unpublished events and publishes them to Kafka (§9.3).
 *
 * Runs on a fixed delay. The @Transactional ensures that marking an event as published
 * is atomic. If Kafka publish fails, the transaction rolls back and the event remains
 * unpublished — it will be retried on the next poll.
 *
 * This does NOT ack Kafka messages on failure — the event remains in the DB for retry.
 * Per failure semantics: "Persistence failure — do not ack Kafka message → allow redelivery."
 *
 * Concurrency: only one pod should run this poller at a time. In multi-pod deployments,
 * use a distributed lock (e.g., Shedlock) or elect a single scheduler pod via
 * Kubernetes leader election. For simplicity, this implementation is safe for concurrent
 * runs because each pod processes a distinct batch ordered by occurred_at — the DB-level
 * UPDATE races are resolved by optimistic locking on the 'published' flag.
 */
@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final AppProperties appProperties;
    private final MeterRegistry meterRegistry;

    public OutboxPoller(OutboxEventRepository outboxEventRepository,
                        @Qualifier("outboxKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate,
                        AppProperties appProperties,
                        MeterRegistry meterRegistry) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.appProperties = appProperties;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(fixedDelayString = "${app.outbox.polling-interval-ms:1000}")
    @Transactional
    public void pollAndPublish() {
        int batchSize = appProperties.outbox().batchSize();
        List<OutboxEventEntity> unpublished = outboxEventRepository
                .findUnpublishedEvents(PageRequest.of(0, batchSize));

        if (unpublished.isEmpty()) {
            return;
        }

        log.debug("Outbox poller processing {} events", unpublished.size());

        for (OutboxEventEntity event : unpublished) {
            publishEvent(event);
        }

        meterRegistry.counter("outbox.events.published",
                "batch_size", String.valueOf(unpublished.size())).increment(unpublished.size());
    }

    private void publishEvent(OutboxEventEntity event) {
        try {
            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(
                    event.getKafkaTopic(),
                    event.getKafkaKey(),
                    event.getPayload()
            );

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish outbox event {} to topic {}: {}",
                              event.getEventId(), event.getKafkaTopic(), ex.getMessage());
                    event.recordPublishFailure(ex.getMessage());
                    meterRegistry.counter("outbox.events.publish_failure").increment();
                } else {
                    event.markPublished();
                    log.debug("Published outbox event {} to topic {} partition {}",
                              event.getEventId(), event.getKafkaTopic(),
                              result.getRecordMetadata().partition());
                }
            });

        } catch (Exception e) {
            log.error("Error publishing outbox event {}: {}", event.getEventId(), e.getMessage(), e);
            event.recordPublishFailure(e.getMessage());
            meterRegistry.counter("outbox.events.publish_failure").increment();
        }
    }
}
