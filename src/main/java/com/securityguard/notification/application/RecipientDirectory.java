package com.securityguard.notification.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only boundary for recipient data owned by identity-service. Notification
 * deliberately stores user/company ids rather than identity JPA entities.
 */
public interface RecipientDirectory {

    Optional<Recipient> findRecipient(UUID companyId, UUID userId);

    List<Recipient> findActiveAdmins(UUID companyId);

    record Recipient(UUID userId, String email) {
    }
}
