package com.securityguard.notification.application;

import java.util.List;

/**
 * What the Notification module subscribes to, by STABLE event-type string. Deliberately not imported
 * from the incident module: the contract between the modules is "incident.assigned v1", not a Java
 * class, so the two can be separated into different services without code changes here.
 */
public final class NotificationEventSubscriptions {

    public static final String CONSUMER_NAME = "notification";
    public static final String QUEUE = "securityguard.notification.events";

    public static final String INCIDENT_CREATED = "incident.created";
    public static final String INCIDENT_ASSIGNED = "incident.assigned";
    public static final String INCIDENT_RESOLVED = "incident.resolved";
    public static final String EQUIPMENT_ISSUE_DETECTED = "equipment.issue_detected";

    /** Exact bindings: the queue only receives events this consumer actually handles. */
    public static final List<String> ROUTING_KEYS =
            List.of(INCIDENT_CREATED, INCIDENT_ASSIGNED, INCIDENT_RESOLVED, EQUIPMENT_ISSUE_DETECTED);

    public static final int SUPPORTED_VERSION = 1;

    private NotificationEventSubscriptions() {
    }
}
