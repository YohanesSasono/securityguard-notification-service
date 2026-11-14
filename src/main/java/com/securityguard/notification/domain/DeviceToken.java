package com.securityguard.notification.domain;

import com.securityguard.persistence.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A token is physically tied to one device, not permanently to one account
 * (a user can log out and a different user can log in on the same device).
 * Registration is therefore an upsert keyed on the token value, reassigning
 * `user` if the token already exists - see NotificationService.registerDevice.
 */
@Entity
@Table(name = "device_tokens")
@Getter
@Setter
@NoArgsConstructor
public class DeviceToken extends AuditableEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token", nullable = false, unique = true, length = 500)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 20)
    private DevicePlatform platform;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;
}
