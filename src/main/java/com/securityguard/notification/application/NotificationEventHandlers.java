package com.securityguard.notification.application;

import com.securityguard.contracts.event.PermanentEventException;
import com.securityguard.contracts.event.EventEnvelope;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * The Notification module's reactions to events. They run INSIDE the idempotency transaction (see
 * TransactionalIdempotentExecutor), and their only effect is {@link NotificationService#enqueue} - a
 * database insert - so "event processed" and "notification job created" commit atomically. Delivery
 * itself (FCM/email) stays asynchronous in NotificationDispatcher, as in Phase 7.
 *
 * <p>Tolerant reader: only the fields needed are read; extra fields are ignored, so adding an optional
 * field to a v1 payload breaks nothing. A MISSING required field is a PermanentEventException (retrying
 * cannot add it). Tenant scoping comes from the envelope's tenantId, which the registry guarantees is
 * present before these methods run.
 *
 * <p>Notification text and behaviour are unchanged from Phase 7: only the trigger moved from an
 * in-process AFTER_COMMIT listener to the broker.
 */
@Component
public class NotificationEventHandlers {

    private final NotificationService notificationService;
    private final RecipientDirectory recipientDirectory;
    private final ObjectMapper mapper;

    public NotificationEventHandlers(NotificationService notificationService, RecipientDirectory recipientDirectory,
                                     ObjectMapper mapper) {
        this.notificationService = notificationService;
        this.recipientDirectory = recipientDirectory;
        this.mapper = mapper;
    }

    public void onIncidentAssigned(EventEnvelope event) {
        JsonNode payload = payload(event);
        UUID incidentId = requiredUuid(payload, "incidentId");
        UUID assignee = requiredUuid(payload, "assignedToUserId");
        var content = new NotificationService.NotificationContent(
                "You've been assigned an incident",
                "An incident has been assigned to you and requires attention.",
                "INCIDENT", incidentId);
        notificationService.enqueue(event.tenantId(), assignee,
                "INCIDENT_ASSIGNED:" + incidentId + ":" + assignee, content);
    }

    public void onIncidentCreated(EventEnvelope event) {
        JsonNode payload = payload(event);
        UUID incidentId = requiredUuid(payload, "incidentId");
        String severity = requiredText(payload, "severity");
        if (!"HIGH".equals(severity) && !"CRITICAL".equals(severity)) {
            return; // only high-severity incidents alert admins (unchanged from Phase 7)
        }
        notifyAdmins(event.tenantId(), "INCIDENT_CREATED:" + incidentId, "High-severity incident reported",
                "A " + severity + " severity incident was just reported and needs review.", incidentId);
    }

    public void onIncidentResolved(EventEnvelope event) {
        UUID incidentId = requiredUuid(payload(event), "incidentId");
        notifyAdmins(event.tenantId(), "INCIDENT_RESOLVED:" + incidentId, "Incident resolved",
                "An incident has been marked resolved and is awaiting closure review.", incidentId);
    }

    public void onEquipmentIssueDetected(EventEnvelope event) {
        JsonNode payload = payload(event);
        UUID incidentId = requiredUuid(payload, "incidentId");
        String severity = requiredText(payload, "severity");
        notifyAdmins(event.tenantId(), "EQUIPMENT_ISSUE:" + incidentId, "Equipment issue detected",
                "A " + severity + " severity equipment issue was detected during a patrol.", incidentId);
    }

    private void notifyAdmins(UUID companyId, String dedupeKeyPrefix, String title, String body, UUID incidentId) {
        var content = new NotificationService.NotificationContent(title, body, "INCIDENT", incidentId);
        for (RecipientDirectory.Recipient admin : recipientDirectory.findActiveAdmins(companyId)) {
            notificationService.enqueue(companyId, admin.userId(), dedupeKeyPrefix + ":" + admin.userId(), content);
        }
    }

    private JsonNode payload(EventEnvelope event) {
        try {
            return mapper.readTree(event.payload());
        } catch (JacksonException e) {
            throw new PermanentEventException("Payload is not valid JSON", e);
        }
    }

    private static UUID requiredUuid(JsonNode payload, String field) {
        String text = requiredText(payload, field);
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            throw new PermanentEventException("Payload field '" + field + "' is not a UUID");
        }
    }

    private static String requiredText(JsonNode payload, String field) {
        JsonNode node = payload.get(field);
        if (node == null || node.isNull() || node.asText().isBlank()) {
            throw new PermanentEventException("Payload is missing required field '" + field + "'");
        }
        return node.asText();
    }
}
