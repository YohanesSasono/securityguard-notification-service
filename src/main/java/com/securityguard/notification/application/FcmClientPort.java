package com.securityguard.notification.application;

/**
 * Port for the actual FCM send call. `LoggingFcmClient` (infrastructure) is
 * a placeholder that logs and always succeeds - no Firebase Admin SDK
 * dependency or real project credentials are available in this environment.
 * A real adapter swaps in behind this interface with no change to
 * NotificationDispatcher. Never exposes the underlying FCM server
 * credentials through this interface or anywhere else in the API.
 */
public interface FcmClientPort {

    enum SendOutcome {
        SUCCESS,
        /** Worth retrying later (network blip, FCM-side rate limit, etc). */
        TRANSIENT_FAILURE,
        /** Token is invalid/unregistered - never retry this token again. */
        INVALID_TOKEN
    }

    SendOutcome send(String deviceToken, String title, String body);
}
