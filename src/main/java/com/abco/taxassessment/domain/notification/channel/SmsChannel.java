package com.abco.taxassessment.domain.notification.channel;

import com.abco.taxassessment.config.properties.AppProperties;
import com.abco.taxassessment.domain.notification.domain.entity.NotificationEventEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * SMS notification channel via Twilio.
 *
 * Credentials loaded from Vault CSI secrets at runtime — never from application config (§13).
 * Resilience: Retry → CircuitBreaker per §11.
 */
@Component
public class SmsChannel {

    private static final Logger log = LoggerFactory.getLogger(SmsChannel.class);

    private final AppProperties.TwilioProperties twilioConfig;
    private final ObjectMapper objectMapper;

    public SmsChannel(AppProperties appProperties, ObjectMapper objectMapper) {
        this.twilioConfig = appProperties.notification().twilio();
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        Twilio.init(twilioConfig.accountSid(), twilioConfig.authToken());
    }

    @Retry(name = "notification-sms")
    @CircuitBreaker(name = "notification-sms")
    public String send(NotificationEventEntity event) {
        try {
            JsonNode payload = objectMapper.readTree(event.getPayload());
            String body = payload.path("smsBody").asText(
                    payload.path("subject").asText("ABCO Tax Assessment: Action Required"));

            Message message = Message.creator(
                    new PhoneNumber(event.getRecipient()),
                    new PhoneNumber(twilioConfig.fromNumber()),
                    body
            ).create();

            log.info("SMS sent to {} event_type={} sid={}",
                     event.getRecipient(), event.getEventType(), message.getSid());
            return message.getSid();

        } catch (Exception e) {
            log.error("Failed to send SMS to {}: {}", event.getRecipient(), e.getMessage());
            throw new RuntimeException("SMS send failed: " + e.getMessage(), e);
        }
    }
}
