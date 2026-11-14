package com.securityguard.notification.infrastructure;

import com.securityguard.messaging.broker.rabbit.EventTopology.ConsumerQueueSpec;
import com.securityguard.messaging.consumer.ConsumerPipeline;
import com.securityguard.messaging.consumer.ConsumerRetryPolicy;
import com.securityguard.messaging.consumer.EventHandlerRegistry;
import com.securityguard.messaging.consumer.idempotency.IdempotentExecutor;
import com.securityguard.notification.application.NotificationEventHandlers;
import com.securityguard.notification.application.NotificationEventSubscriptions;
import com.securityguard.observability.metrics.MetricsRecorder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Subscribes the Notification module: contributes its queue (which the messaging module declares with
 * retry tiers and a DLQ) and the handler table - one handler per (event type, version) it understands.
 * To support a v2 of an event later, register a second handler for it; v1 keeps working alongside.
 */
@Configuration
@ConditionalOnExpression("'${app.messaging.broker.type:}' == 'rabbit' && '${app.messaging.consumer.enabled:false}' == 'true'")
public class NotificationEventConsumerConfiguration {

    @Bean
    public ConsumerQueueSpec notificationQueueSpec() {
        return new ConsumerQueueSpec(NotificationEventSubscriptions.QUEUE, NotificationEventSubscriptions.ROUTING_KEYS);
    }

    @Bean
    public ConsumerPipeline notificationPipeline(NotificationEventHandlers handlers, IdempotentExecutor executor,
                                                 ConsumerRetryPolicy retryPolicy, MetricsRecorder metrics) {
        int v = NotificationEventSubscriptions.SUPPORTED_VERSION;
        EventHandlerRegistry registry = new EventHandlerRegistry()
                .registerTenantScoped(NotificationEventSubscriptions.INCIDENT_CREATED, v, handlers::onIncidentCreated)
                .registerTenantScoped(NotificationEventSubscriptions.INCIDENT_ASSIGNED, v, handlers::onIncidentAssigned)
                .registerTenantScoped(NotificationEventSubscriptions.INCIDENT_RESOLVED, v, handlers::onIncidentResolved)
                .registerTenantScoped(NotificationEventSubscriptions.EQUIPMENT_ISSUE_DETECTED, v,
                        handlers::onEquipmentIssueDetected);
        return new ConsumerPipeline(NotificationEventSubscriptions.CONSUMER_NAME, registry, executor, retryPolicy, metrics);
    }
}
