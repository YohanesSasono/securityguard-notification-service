package com.securityguard.notification.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Retry/backoff tuning is deployment-configurable rather than hardcoded, so
 * the dispatcher's behavior can be tuned without a code change - the same
 * seam a future outbox/broker-based implementation would also need exposed.
 */
@Validated
@ConfigurationProperties(prefix = "app.notification")
public class NotificationProperties {

    /** How often the dispatcher polls for due notifications. */
    @Min(500)
    private long dispatchIntervalMs = 5000;

    /** How many candidate notifications one dispatch cycle considers. */
    @Min(1)
    private int batchSize = 50;

    /** After this many failed attempts, a transient failure becomes permanent (no more retries). */
    @Min(1)
    private int maxAttempts = 5;

    /** Base delay for exponential backoff: attempt N waits baseBackoffSeconds * 2^(N-1), capped at maxBackoffSeconds. */
    @Min(1)
    private long baseBackoffSeconds = 30;

    @Min(1)
    private long maxBackoffSeconds = 3600;

    /** Upper bounds for calls to external delivery providers while a notification row is locked. */
    @Min(1)
    private long fcmTimeoutMs = 3000;

    @Min(1)
    private long emailTimeoutMs = 5000;

    public long getDispatchIntervalMs() {
        return dispatchIntervalMs;
    }

    public void setDispatchIntervalMs(long dispatchIntervalMs) {
        this.dispatchIntervalMs = dispatchIntervalMs;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getBaseBackoffSeconds() {
        return baseBackoffSeconds;
    }

    public void setBaseBackoffSeconds(long baseBackoffSeconds) {
        this.baseBackoffSeconds = baseBackoffSeconds;
    }

    public long getMaxBackoffSeconds() {
        return maxBackoffSeconds;
    }

    public void setMaxBackoffSeconds(long maxBackoffSeconds) {
        this.maxBackoffSeconds = maxBackoffSeconds;
    }

    public long getFcmTimeoutMs() {
        return fcmTimeoutMs;
    }

    public void setFcmTimeoutMs(long fcmTimeoutMs) {
        this.fcmTimeoutMs = fcmTimeoutMs;
    }

    public long getEmailTimeoutMs() {
        return emailTimeoutMs;
    }

    public void setEmailTimeoutMs(long emailTimeoutMs) {
        this.emailTimeoutMs = emailTimeoutMs;
    }
}
