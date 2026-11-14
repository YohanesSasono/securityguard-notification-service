package com.securityguard.notification.presentation.dto;

import com.securityguard.notification.domain.DeviceToken;

import java.time.Instant;
import java.util.UUID;

/** Never includes anything beyond the client's own token value - no server-side FCM credentials exist to leak here. */
public record DeviceResponse(UUID id, String platform, boolean active, Instant lastSeenAt, Instant createdAt) {
    public static DeviceResponse from(DeviceToken deviceToken) {
        return new DeviceResponse(
                deviceToken.getId(), deviceToken.getPlatform().name(), deviceToken.isActive(),
                deviceToken.getLastSeenAt(), deviceToken.getCreatedAt());
    }
}
