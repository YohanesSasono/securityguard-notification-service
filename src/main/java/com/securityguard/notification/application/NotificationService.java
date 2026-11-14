package com.securityguard.notification.application;

import com.securityguard.notification.domain.*;
import com.securityguard.notification.domain.repository.DeviceTokenRepository;
import com.securityguard.notification.domain.repository.NotificationPreferenceRepository;
import com.securityguard.notification.domain.repository.NotificationRepository;
import com.securityguard.shared.exception.ResourceNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant isolation: every method takes companyId derived from the caller's
 * JWT (never client-supplied) and re-derives ownership through the user,
 * not a client-supplied userId - a device token can only ever be registered
 * to the authenticated caller, and preferences/history are always the
 * caller's own (API_SPEC_V2.txt endpoints don't expose "view another user's
 * notifications" at all, so there's no cross-user path to even test here).
 */
@Service
@Transactional
public class NotificationService {

    private final DeviceTokenRepository deviceTokenRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final NotificationRepository notificationRepository;
    private final RecipientDirectory recipientDirectory;

    public NotificationService(DeviceTokenRepository deviceTokenRepository,
                               NotificationPreferenceRepository notificationPreferenceRepository,
                               NotificationRepository notificationRepository,
                               RecipientDirectory recipientDirectory) {
        this.deviceTokenRepository = deviceTokenRepository;
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.notificationRepository = notificationRepository;
        this.recipientDirectory = recipientDirectory;
    }

    public record RegisterDeviceCommand(String token, DevicePlatform platform) {
    }

    public record UpdatePreferencesCommand(Boolean fcmEnabled, Boolean emailEnabled) {
    }

    /** Content to notify a user about, independent of channel - NotificationService fans this out per enabled channel. */
    public record NotificationContent(String title, String body, String relatedEntityType, UUID relatedEntityId) {
    }

    // ---------------------------------------------------------------
    // Device registration
    // ---------------------------------------------------------------

    /**
     * Upsert keyed on the token value: re-registering the same token (e.g.
     * app reinstall, or a different user logging in on the same device)
     * reassigns ownership rather than erroring - a device token belongs to
     * whoever most recently registered it, not permanently to one account.
     */
    public DeviceToken registerDevice(UUID companyId, UUID userId, RegisterDeviceCommand command) {
        requireRecipient(companyId, userId);
        DeviceToken deviceToken = deviceTokenRepository.findByToken(command.token())
                .orElseGet(DeviceToken::new);
        deviceToken.setUserId(userId);
        deviceToken.setToken(command.token());
        deviceToken.setPlatform(command.platform());
        deviceToken.setActive(true);
        deviceToken.setLastSeenAt(Instant.now());

        try {
            return deviceTokenRepository.save(deviceToken);
        } catch (DataIntegrityViolationException e) {
            DeviceToken existing = deviceTokenRepository.findByToken(command.token())
                    .orElseThrow(() -> e);
            existing.setUserId(userId);
            existing.setPlatform(command.platform());
            existing.setActive(true);
            existing.setLastSeenAt(Instant.now());
            return deviceTokenRepository.save(existing);
        }
    }

    // ---------------------------------------------------------------
    // Preferences
    // ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public NotificationPreference getPreferences(UUID companyId, UUID userId) {
        requireRecipient(companyId, userId);
        return notificationPreferenceRepository.findByUserId(userId)
                .orElseGet(() -> defaultPreference(userId));
    }

    public NotificationPreference updatePreferences(UUID companyId, UUID userId, UpdatePreferencesCommand command) {
        requireRecipient(companyId, userId);

        NotificationPreference preference = notificationPreferenceRepository.findByUserId(userId)
                .orElseGet(() -> defaultPreference(userId));

        if (command.fcmEnabled() != null) {
            preference.setFcmEnabled(command.fcmEnabled());
        }
        if (command.emailEnabled() != null) {
            preference.setEmailEnabled(command.emailEnabled());
        }
        return notificationPreferenceRepository.save(preference);
    }

    private NotificationPreference defaultPreference(UUID userId) {
        NotificationPreference preference = new NotificationPreference();
        preference.setUserId(userId);
        preference.setFcmEnabled(true);
        preference.setEmailEnabled(true);
        return preference; // not persisted until the caller actually changes something via updatePreferences
    }

    // ---------------------------------------------------------------
    // History
    // ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<Notification> listHistory(UUID companyId, UUID userId, Pageable pageable) {
        return notificationRepository.findByCompanyIdAndRecipientUserIdOrderByCreatedAtDesc(companyId, userId, pageable);
    }

    // ---------------------------------------------------------------
    // Enqueue (called from event listeners)
    // ---------------------------------------------------------------

    /**
     * Fans the content out to every channel the recipient has enabled,
     * skipping channels they've disabled. Each (event-derived dedupeKey,
     * channel) pair is only ever enqueued once - a duplicate call with the
     * same dedupeKeyPrefix+channel returns the existing row instead of
     * creating a second one (DB unique constraint is the real guarantee;
     * this pre-check just avoids a needless constraint-violation round trip).
     */
    public void enqueue(UUID companyId, UUID recipientUserId, String dedupeKeyPrefix, NotificationContent content) {
        Optional<RecipientDirectory.Recipient> recipient = recipientDirectory.findRecipient(companyId, recipientUserId);
        if (recipient.isEmpty()) {
            return; // recipient no longer exists/belongs to this tenant - nothing to notify
        }

        NotificationPreference preference = notificationPreferenceRepository.findByUserId(recipientUserId)
                .orElseGet(() -> defaultPreference(recipientUserId));

        if (preference.isFcmEnabled()) {
            enqueueOneChannel(companyId, recipient.get(), NotificationChannel.FCM, dedupeKeyPrefix, content);
        }
        if (preference.isEmailEnabled()) {
            enqueueOneChannel(companyId, recipient.get(), NotificationChannel.EMAIL, dedupeKeyPrefix, content);
        }
    }

    private void enqueueOneChannel(UUID companyId, RecipientDirectory.Recipient recipient, NotificationChannel channel,
                                    String dedupeKeyPrefix, NotificationContent content) {
        String dedupeKey = dedupeKeyPrefix + ":" + channel.name();
        if (notificationRepository.findByDedupeKey(dedupeKey).isPresent()) {
            return; // already enqueued for this (event, recipient, channel) - idempotent no-op
        }

        Notification notification = new Notification();
        notification.setCompanyId(companyId);
        notification.setRecipientUserId(recipient.userId());
        notification.setRecipientEmail(recipient.email());
        notification.setChannel(channel);
        notification.setTitle(content.title());
        notification.setBody(content.body());
        notification.setRelatedEntityType(content.relatedEntityType());
        notification.setRelatedEntityId(content.relatedEntityId());
        notification.setDedupeKey(dedupeKey);
        notification.setNextAttemptAt(Instant.now());

        try {
            notificationRepository.save(notification);
        } catch (DataIntegrityViolationException e) {
            // Race: two concurrent enqueue attempts for the same dedupe key.
            // The DB constraint is the real guarantee; losing this race is a
            // no-op, not an error, since the desired row already exists.
        }
    }

    private RecipientDirectory.Recipient requireRecipient(UUID companyId, UUID userId) {
        return recipientDirectory.findRecipient(companyId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
