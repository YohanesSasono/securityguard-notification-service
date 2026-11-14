package com.securityguard.notification.application;

import com.securityguard.notification.config.NotificationProperties;
import com.securityguard.notification.domain.*;
import com.securityguard.notification.domain.repository.DeviceTokenRepository;
import com.securityguard.notification.domain.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * <h2>Dispatch strategy</h2>
 * A lightweight, database-backed job queue rather than a real message
 * broker (that's Phase 10's transactional-outbox/broker work) - but built
 * so the seam is already in the right place: {@code enqueue} in
 * {@link NotificationService} writes a durable, retryable row; this class
 * is the "consumer" that would be replaced by a broker subscription later,
 * with no change to how notifications get created.
 *
 * <h2>Concurrent processing</h2>
 * Two steps, deliberately not one: {@link #dispatchDueNotifications()} does
 * a plain, non-locking read to find candidate ids, then calls
 * {@link #processOne(UUID)} - a separate {@code @Transactional} method,
 * invoked through a {@code @Lazy} self-proxy so it genuinely gets its own
 * transaction (same self-invocation reasoning as {@code PatrolExecutionService}
 * and {@code IncidentService}) - for each one. {@code processOne} then
 * issues {@code SELECT ... FOR UPDATE SKIP LOCKED} for that specific id: if
 * another thread or app instance already grabbed it between the candidate
 * read and now, this call gets nothing back and simply returns. This is the
 * correct way to let multiple dispatchers (multiple threads, or multiple
 * running instances of this service) compete for the same due notifications
 * without ever double-sending one.
 *
 * <h2>Retry / backoff</h2>
 * A transient failure increments {@code attemptCount} and reschedules
 * {@code nextAttemptAt} with exponential backoff; once {@code attemptCount}
 * reaches {@code app.notification.max-attempts}, or the failure is
 * classified as permanent (invalid/unregistered device token, or no active
 * device at all), the notification is marked {@code FAILED} and never
 * retried again (ARCHITECTURE_V2.txt: "do not retry permanent failures").
 */
@Component
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private enum DispatchResult { SUCCESS, TRANSIENT_FAILURE, PERMANENT_FAILURE }

    private final NotificationDispatcher self;
    private final NotificationRepository notificationRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final FcmClientPort fcmClientPort;
    private final EmailSenderPort emailSenderPort;
    private final RecipientDirectory recipientDirectory;
    private final NotificationProperties notificationProperties;

    public NotificationDispatcher(@Lazy NotificationDispatcher self,
                                   NotificationRepository notificationRepository,
                                   DeviceTokenRepository deviceTokenRepository,
                                   FcmClientPort fcmClientPort,
                                   EmailSenderPort emailSenderPort,
                                   RecipientDirectory recipientDirectory,
                                   NotificationProperties notificationProperties) {
        this.self = self;
        this.notificationRepository = notificationRepository;
        this.deviceTokenRepository = deviceTokenRepository;
        this.fcmClientPort = fcmClientPort;
        this.emailSenderPort = emailSenderPort;
        this.recipientDirectory = recipientDirectory;
        this.notificationProperties = notificationProperties;
    }

    @Scheduled(fixedDelayString = "${app.notification.dispatch-interval-ms:5000}")
    public void dispatchDueNotifications() {
        List<UUID> dueIds = notificationRepository.findDueIds(
                Instant.now(), PageRequest.of(0, notificationProperties.getBatchSize()));
        for (UUID id : dueIds) {
            try {
                self.processOne(id);
            } catch (Exception e) {
                log.error("Unexpected error processing notificationId={}", id, e);
            }
        }
    }

    @Transactional
    public void processOne(UUID notificationId) {
        Optional<Notification> claimed = notificationRepository.claimForUpdate(notificationId);
        if (claimed.isEmpty()) {
            return; // lost the race to another dispatcher, or already handled
        }
        Notification notification = claimed.get();
        if (notification.getStatus() != NotificationStatus.PENDING) {
            return; // defensive: state changed between the candidate read and this claim
        }

        DispatchResult result = attemptSend(notification);
        applyResult(notification, result);
        notificationRepository.save(notification);
    }

    private DispatchResult attemptSend(Notification notification) {
        if (notification.getChannel() == NotificationChannel.FCM) {
            return sendViaFcm(notification);
        }
        return sendViaEmail(notification);
    }

    private DispatchResult sendViaFcm(Notification notification) {
        List<DeviceToken> tokens = deviceTokenRepository.findByUserIdAndActiveTrue(notification.getRecipientUserId());
        if (tokens.isEmpty()) {
            return DispatchResult.PERMANENT_FAILURE;
        }

        boolean anySucceeded = false;
        boolean anyTransientFailure = false;
        for (DeviceToken token : tokens) {
            FcmClientPort.SendOutcome outcome = fcmClientPort.send(token.getToken(), notification.getTitle(), notification.getBody());
            switch (outcome) {
                case SUCCESS -> anySucceeded = true;
                case TRANSIENT_FAILURE -> anyTransientFailure = true;
                case INVALID_TOKEN -> {
                    token.setActive(false);
                    deviceTokenRepository.save(token);
                }
            }
        }

        if (anySucceeded) {
            return DispatchResult.SUCCESS;
        }
        return anyTransientFailure ? DispatchResult.TRANSIENT_FAILURE : DispatchResult.PERMANENT_FAILURE;
    }

    private DispatchResult sendViaEmail(Notification notification) {
        String email = notification.getRecipientEmail();
        if (email == null || email.isBlank()) {
            // Backward compatibility for rows created before recipient_email existed.
            Optional<RecipientDirectory.Recipient> recipient = recipientDirectory.findRecipient(
                    notification.getCompanyId(), notification.getRecipientUserId());
            if (recipient.isEmpty()) {
                return DispatchResult.PERMANENT_FAILURE;
            }
            email = recipient.get().email();
        }
        EmailSenderPort.SendOutcome outcome = emailSenderPort.send(email, notification.getTitle(), notification.getBody());
        return switch (outcome) {
            case SUCCESS -> DispatchResult.SUCCESS;
            case TRANSIENT_FAILURE -> DispatchResult.TRANSIENT_FAILURE;
            case PERMANENT_FAILURE -> DispatchResult.PERMANENT_FAILURE;
        };
    }

    private void applyResult(Notification notification, DispatchResult result) {
        notification.setLastAttemptAt(Instant.now());

        if (result == DispatchResult.SUCCESS) {
            notification.setStatus(NotificationStatus.SENT);
            return;
        }

        notification.setAttemptCount(notification.getAttemptCount() + 1);

        boolean exhausted = notification.getAttemptCount() >= notificationProperties.getMaxAttempts();
        if (result == DispatchResult.PERMANENT_FAILURE || exhausted) {
            notification.setStatus(NotificationStatus.FAILED);
            return;
        }

        notification.setStatus(NotificationStatus.PENDING);
        notification.setNextAttemptAt(Instant.now().plusSeconds(backoffSeconds(notification.getAttemptCount())));
    }

    private long backoffSeconds(int attemptCount) {
        long base = notificationProperties.getBaseBackoffSeconds();
        long backoff = base * (1L << Math.max(0, attemptCount - 1));
        return Math.min(backoff, notificationProperties.getMaxBackoffSeconds());
    }
}
