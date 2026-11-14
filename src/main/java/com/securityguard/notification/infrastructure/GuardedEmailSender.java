package com.securityguard.notification.infrastructure;

import com.securityguard.notification.application.EmailSenderPort;
import com.securityguard.observability.metrics.MetricNames;
import com.securityguard.observability.metrics.MetricsRecorder;
import com.securityguard.resilience.CallTimeoutException;
import com.securityguard.resilience.CircuitOpenException;
import com.securityguard.resilience.ExternalCallGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The e-mail counterpart of {@link GuardedFcmClient}: same contract enforcement, same retry owner (the
 * notification dispatcher's database backoff). {@code PERMANENT_FAILURE} (e.g. a rejected address) is an
 * expected answer and never counts against the provider's health.
 */
public final class GuardedEmailSender implements EmailSenderPort {

    private static final Logger log = LoggerFactory.getLogger(GuardedEmailSender.class);

    private final EmailSenderPort delegate;
    private final ExternalCallGuard guard;
    private final MetricsRecorder metrics;

    public GuardedEmailSender(EmailSenderPort delegate, ExternalCallGuard guard, MetricsRecorder metrics) {
        this.delegate = delegate;
        this.guard = guard;
        this.metrics = metrics == null ? MetricsRecorder.NOOP : metrics;
    }

    @Override
    public SendOutcome send(String toEmail, String subject, String body) {
        SendOutcome outcome;
        String label;
        try {
            outcome = guard.call(() -> delegate.send(toEmail, subject, body), t -> true, o -> o == SendOutcome.TRANSIENT_FAILURE);
            label = switch (outcome) {
                case SUCCESS -> "success";
                case TRANSIENT_FAILURE -> "transient_failure";
                case PERMANENT_FAILURE -> "permanent_failure";
            };
        } catch (CircuitOpenException e) {
            outcome = SendOutcome.TRANSIENT_FAILURE;
            label = "circuit_open";
        } catch (CallTimeoutException e) {
            outcome = SendOutcome.TRANSIENT_FAILURE;
            label = "timeout";
        } catch (RuntimeException e) {
            log.warn("Email adapter threw {}; treating as a transient failure", e.getClass().getSimpleName());
            outcome = SendOutcome.TRANSIENT_FAILURE;
            label = "transient_failure";
        }
        metrics.increment(MetricNames.NOTIFICATION_DISPATCH, "channel", "email", "outcome", label);
        return outcome;
    }
}
