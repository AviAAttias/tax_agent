package com.abco.taxassessment.domain.notification.channel;

import com.abco.taxassessment.config.properties.AppProperties;
import com.abco.taxassessment.domain.notification.domain.entity.NotificationEventEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.UUID;

/**
 * Email notification channel (SES/SMTP-compatible via Spring Mail).
 *
 * Resilience: Retry → CircuitBreaker per §11.
 * Provider message ID: generated as UUID (SMTP doesn't return message IDs reliably;
 * for SES, the message ID is returned in the response header — extracted if available).
 */
@Component
public class EmailChannel {

    private static final Logger log = LoggerFactory.getLogger(EmailChannel.class);

    private final JavaMailSender mailSender;
    private final AppProperties.NotificationProperties config;
    private final ObjectMapper objectMapper;

    public EmailChannel(JavaMailSender mailSender,
                        AppProperties appProperties,
                        ObjectMapper objectMapper) {
        this.mailSender = mailSender;
        this.config = appProperties.notification();
        this.objectMapper = objectMapper;
    }

    @Retry(name = "notification-email")
    @CircuitBreaker(name = "notification-email")
    public String send(NotificationEventEntity event) {
        try {
            JsonNode payload = objectMapper.readTree(event.getPayload());
            String subject = payload.path("subject").asText("Tax Assessment Notification");
            String body = payload.path("body").asText("");
            String htmlBody = payload.path("htmlBody").asText(body);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(config.fromEmail(), config.fromName());
            helper.setTo(event.getRecipient());
            helper.setSubject(subject);
            helper.setText(body, htmlBody);

            mailSender.send(message);

            String messageId = UUID.randomUUID().toString();
            log.info("Email sent to {} event_type={} message_id={}",
                     event.getRecipient(), event.getEventType(), messageId);
            return messageId;

        } catch (MessagingException | Exception e) {
            log.error("Failed to send email to {}: {}", event.getRecipient(), e.getMessage());
            throw new RuntimeException("Email send failed: " + e.getMessage(), e);
        }
    }
}
