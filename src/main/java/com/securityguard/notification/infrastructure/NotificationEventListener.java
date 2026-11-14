package com.securityguard.notification.infrastructure;

import com.securityguard.messaging.broker.rabbit.RabbitConsumerAdapter;
import com.securityguard.messaging.consumer.ConsumerPipeline;
import com.securityguard.notification.application.NotificationEventSubscriptions;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * The broker entry point for the Notification module - deliberately thin. All decisions (idempotency,
 * retry, dead-lettering) live in the pipeline; this just hands the delivery over.
 *
 * <p>Notification is eventually consistent with the incident API: the HTTP call that assigns or resolves
 * an incident returns as soon as the business change and its outbox row commit. The notification is
 * created a moment later by this consumer, and delivered a moment after that by NotificationDispatcher.
 */
@Component
@ConditionalOnExpression("'${app.messaging.broker.type:}' == 'rabbit' && '${app.messaging.consumer.enabled:false}' == 'true'")
public class NotificationEventListener {

    private final RabbitConsumerAdapter adapter;
    private final ConsumerPipeline pipeline;

    public NotificationEventListener(RabbitConsumerAdapter adapter,
                                     @Qualifier("notificationPipeline") ConsumerPipeline pipeline) {
        this.adapter = adapter;
        this.pipeline = pipeline;
    }

    @RabbitListener(queues = NotificationEventSubscriptions.QUEUE)
    public void onMessage(Message message) {
        adapter.handle(message, pipeline, NotificationEventSubscriptions.QUEUE);
    }
}
