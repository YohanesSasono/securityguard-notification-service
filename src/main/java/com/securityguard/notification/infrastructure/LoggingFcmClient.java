package com.securityguard.notification.infrastructure;

import com.securityguard.notification.application.FcmClientPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Placeholder adapter: real Firebase Cloud Messaging delivery requires the
 * Firebase Admin SDK and a real service-account credential, neither of
 * which are available/appropriate to add in this environment. Logs the
 * payload (never the credential, since there is none here) and always
 * reports SUCCESS, proving the port boundary end to end. Swapping in a real
 * Firebase Admin SDK-based adapter requires no change to NotificationDispatcher.
 */
@Component
public class LoggingFcmClient implements FcmClientPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingFcmClient.class);

    @Override
    public SendOutcome send(String deviceToken, String title, String body) {
        log.info("FCM send (stub): tokenSuffix=...{} title='{}'",
                deviceToken.length() > 6 ? deviceToken.substring(deviceToken.length() - 6) : deviceToken, title);
        return SendOutcome.SUCCESS;
    }
}
