package com.securityguard.notification.application;

/**
 * Email foundation per the Phase 7 task ("email foundation if appropriate").
 * `LoggingEmailSender` (infrastructure) is a placeholder - no SMTP/email
 * provider is configured in this environment, mirroring the FCM port.
 */
public interface EmailSenderPort {

    enum SendOutcome { SUCCESS, TRANSIENT_FAILURE, PERMANENT_FAILURE }

    SendOutcome send(String toEmail, String subject, String body);
}
