package com.abco.taxassessment.domain.notification.channel;

import com.abco.taxassessment.domain.notification.domain.entity.NotificationEventEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * In-app notification channel via WebSocket (STOMP over SockJS).
 *
 * Messages are published to /topic/notifications/{tenantId}.
 * The NotificationEventEntity is persisted first (by the dispatcher),
 * so if WebSocket delivery fails (client disconnected), the event is
 * still queryable via the in-app notification inbox API.
 *
 * At-least-once guarantee: the event is in the DB regardless of WebSocket success.
 */
@Component
public class InAppChannel {

    private static final Logger log = LoggerFactory.getLogger(InAppChannel.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public InAppChannel(SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    public String send(NotificationEventEntity event) {
        String destination = "/topic/notifications/" + event.getTenantId();
        try {
            messagingTemplate.convertAndSend(destination, event.getPayload());
            log.debug("In-app notification sent to tenant {} event_type={}",
                      event.getTenantId(), event.getEventType());
        } catch (Exception e) {
            // In-app delivery failure is non-fatal — event persists in DB for inbox query
            log.warn("WebSocket delivery failed for tenant {}: {} (event still in DB)",
                     event.getTenantId(), e.getMessage());
        }
        // Return a deterministic ID for tracking
        return "inapp-" + UUID.randomUUID();
    }
}
