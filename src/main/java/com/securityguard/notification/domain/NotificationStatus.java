package com.securityguard.notification.domain;

/**
 * PENDING covers both "never attempted" and "attempted, transient failure,
 * eligible for another try at next_attempt_at". FAILED is terminal: either
 * retries were exhausted or the failure was permanent (e.g. invalid token) -
 * never retried again (ARCHITECTURE_V2.txt: "do not retry permanent failures").
 */
public enum NotificationStatus { PENDING, SENT, FAILED }
