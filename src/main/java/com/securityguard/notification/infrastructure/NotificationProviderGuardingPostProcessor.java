package com.securityguard.notification.infrastructure;

import com.securityguard.notification.application.EmailSenderPort;
import com.securityguard.notification.application.FcmClientPort;
import com.securityguard.notification.config.NotificationProperties;
import com.securityguard.observability.metrics.MetricsRecorder;
import com.securityguard.resilience.CircuitBreakerRegistry;
import com.securityguard.resilience.ExternalCallGuard;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Wraps every PRODUCTION notification adapter (any {@link FcmClientPort}/{@link EmailSenderPort} bean whose class
 * lives in this infrastructure package - today the logging stubs, tomorrow the real FCM/SMTP adapters) in the
 * guards that enforce the port contract: never throw, never hang, circuit-break a down provider.
 *
 * <p>By package, not by blanket type: test doubles live elsewhere and are left alone, and a future real
 * adapter added to this package is protected automatically with no further wiring.
 */
@Component
public class NotificationProviderGuardingPostProcessor implements BeanPostProcessor {

    private static final String PACKAGE = NotificationProviderGuardingPostProcessor.class.getPackageName();

    private final BeanFactory beanFactory;
    private final NotificationProperties notificationProperties;

    public NotificationProviderGuardingPostProcessor(BeanFactory beanFactory, NotificationProperties notificationProperties) {
        this.beanFactory = beanFactory;
        this.notificationProperties = notificationProperties;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (!bean.getClass().getPackageName().equals(PACKAGE) || bean instanceof GuardedFcmClient || bean instanceof GuardedEmailSender) {
            return bean;
        }
        if (bean instanceof FcmClientPort fcm) {
            return new GuardedFcmClient(fcm, guard("fcm", timeout(true)), metrics());
        }
        if (bean instanceof EmailSenderPort email) {
            return new GuardedEmailSender(email, guard("email", timeout(false)), metrics());
        }
        return bean;
    }

    private ExternalCallGuard guard(String name, Duration timeout) {
        return new ExternalCallGuard(name, beanFactory.getBean(CircuitBreakerRegistry.class).get(name), timeout, metrics());
    }

    private Duration timeout(boolean fcm) {
        return Duration.ofMillis(fcm ? notificationProperties.getFcmTimeoutMs() : notificationProperties.getEmailTimeoutMs());
    }

    private MetricsRecorder metrics() {
        return beanFactory.getBean(MetricsRecorder.class);
    }
}
