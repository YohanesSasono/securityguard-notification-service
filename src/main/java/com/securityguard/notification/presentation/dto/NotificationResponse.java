package com.securityguard.notification.presentation.dto;

import com.securityguard.notification.domain.Notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id, String channel, String title, String body, String relatedEntityType,
        UUID relatedEntityId, String status, Instant createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(), notification.getChannel().name(), notification.getTitle(),
                notification.getBody(), notification.getRelatedEntityType(), notification.getRelatedEntityId(),
                notification.getStatus().name(), notification.getCreatedAt());
    }
}
