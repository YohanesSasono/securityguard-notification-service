package com.securityguard.notification.domain;

import com.securityguard.persistence.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * `relatedEntityType`/`relatedEntityId` are plain String/UUID, not a JPA
 * relationship - Notification must not depend on Incident's (or any other
 * module's) entities directly. `dedupeKey` is deterministic per
 * (event type, related entity, recipient, channel) so a duplicate event
 * delivery can never create a second row - see the V10 migration comment.
 *
 * @Version is a defensive extra layer; the primary concurrency guarantee
 * for the dispatcher's claim-and-process cycle is a native SELECT ... FOR
 * UPDATE SKIP LOCKED query, not optimistic locking - see NotificationDispatcher.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification extends AuditableEntity {

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    /**
     * Delivery-address snapshot captured when the notification is enqueued.
     * It preserves the intended recipient if the user later changes their email.
     * Nullable only to keep pre-existing queued rows deliverable during rollout.
     */
    @Column(name = "recipient_email", length = 320)
    private String recipientEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "body", columnDefinition = "text")
    private String body;

    @Column(name = "related_entity_type", length = 50)
    private String relatedEntityType;

    @Column(name = "related_entity_id")
    private UUID relatedEntityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt = Instant.now();

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "dedupe_key", nullable = false, unique = true, length = 300)
    private String dedupeKey;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
