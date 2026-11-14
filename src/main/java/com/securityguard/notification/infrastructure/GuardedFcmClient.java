package com.securityguard.notification.infrastructure;

import com.securityguard.notification.application.FcmClientPort;
import com.securityguard.observability.metrics.MetricNames;
import com.securityguard.observability.metrics.MetricsRecorder;
import com.securityguard.resilience.CallTimeoutException;
import com.securityguard.resilience.CircuitOpenException;
import com.securityguard.resilience.ExternalCallGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enforces the {@link FcmClientPort} CONTRACT around any adapter: it never throws, never hangs, and stops
 * hammering a provider that is down.
 *
 * <p>Why this matters: {@code NotificationDispatcher.processOne} calls the provider inside a database
 * transaction. If an adapter threw, the transaction rolled back and the notification was left untouched - no
 * attempt counted, no backoff - so it would be retried every dispatcher tick forever. If an adapter hung, a
 * database connection and row lock were held for as long as it hung. Here:
 * <ul>
 *   <li>any exception, timeout or open circuit becomes {@code TRANSIENT_FAILURE}, which the dispatcher already
 *       handles with attempt counting, exponential backoff and a hard attempt limit;</li>
 *   <li>a hard time limit bounds how long the transaction can be held;</li>
 *   <li>repeated provider failures open the circuit so remaining notifications fail fast instead of each
 *       paying the full timeout.</li>
 * </ul>
 * {@code INVALID_TOKEN} is a normal, expected answer and never counts against the provider's health.
 * <b>Retry owner:</b> the dispatcher's database-backed backoff - there is deliberately no retry here.
 */
public final class GuardedFcmClient implements FcmClientPort {

    private static final Logger log = LoggerFactory.getLogger(GuardedFcmClient.class);

    private final FcmClientPort delegate;
    private final ExternalCallGuard guard;
    private final MetricsRecorder metrics;

    public GuardedFcmClient(FcmClientPort delegate, ExternalCallGuard guard, MetricsRecorder metrics) {
        this.delegate = delegate;
        this.guard = guard;
        this.metrics = metrics == null ? MetricsRecorder.NOOP : metrics;
    }

    @Override
    public SendOutcome send(String deviceToken, String title, String body) {
        SendOutcome outcome;
        String label;
        try {
            outcome = guard.call(() -> delegate.send(deviceToken, title, body), t -> true, o -> o == SendOutcome.TRANSIENT_FAILURE);
            label = switch (outcome) {
                case SUCCESS -> "success";
                case TRANSIENT_FAILURE -> "transient_failure";
                case INVALID_TOKEN -> "permanent_failure";
            };
        } catch (CircuitOpenException e) {
            outcome = SendOutcome.TRANSIENT_FAILURE;
            label = "circuit_open";
        } catch (CallTimeoutException e) {
            outcome = SendOutcome.TRANSIENT_FAILURE;
            label = "timeout";
        } catch (RuntimeException e) {
            log.warn("FCM adapter threw {}; treating as a transient failure", e.getClass().getSimpleName());
            outcome = SendOutcome.TRANSIENT_FAILURE;
            label = "transient_failure";
        }
        metrics.increment(MetricNames.NOTIFICATION_DISPATCH, "channel", "fcm", "outcome", label);
        return outcome;
    }
}
