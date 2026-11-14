package com.securityguard.notification.domain;

import com.securityguard.persistence.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Default (row absent) is both channels enabled - an opt-out model, appropriate for security/incident alerts. */
@Entity
@Table(name = "notification_preferences")
@Getter
@Setter
@NoArgsConstructor
public class NotificationPreference extends AuditableEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "fcm_enabled", nullable = false)
    private boolean fcmEnabled = true;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled = true;
}
