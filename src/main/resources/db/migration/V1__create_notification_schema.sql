CREATE SCHEMA IF NOT EXISTS notification;

CREATE TABLE IF NOT EXISTS notification.notifications (
    id UUID PRIMARY KEY,
    created_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by UUID,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    company_id UUID NOT NULL,
    recipient_user_id UUID NOT NULL,
    recipient_email VARCHAR(320),
    channel VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    body TEXT,
    related_entity_type VARCHAR(50),
    related_entity_id UUID,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_attempt_at TIMESTAMP WITH TIME ZONE,
    dedupe_key VARCHAR(300) NOT NULL,
    version BIGINT NOT NULL,

    CONSTRAINT uk_notifications_dedupe_key UNIQUE (dedupe_key),
    CONSTRAINT ck_notifications_channel CHECK (channel IN ('FCM', 'EMAIL')),
    CONSTRAINT ck_notifications_status CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    CONSTRAINT ck_notifications_attempt_count CHECK (attempt_count >= 0)
);

CREATE INDEX IF NOT EXISTS ix_notifications_recipient_history
    ON notification.notifications (company_id, recipient_user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS ix_notifications_pending_dispatch
    ON notification.notifications (next_attempt_at, created_at)
    WHERE status = 'PENDING';

CREATE TABLE IF NOT EXISTS notification.device_tokens (
    id UUID PRIMARY KEY,
    created_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by UUID,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    user_id UUID NOT NULL,
    token VARCHAR(500) NOT NULL,
    platform VARCHAR(20) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    last_seen_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT uk_device_tokens_token UNIQUE (token),
    CONSTRAINT ck_device_tokens_platform CHECK (platform IN ('ANDROID', 'IOS', 'WEB'))
);

CREATE INDEX IF NOT EXISTS ix_device_tokens_active_user
    ON notification.device_tokens (user_id)
    WHERE is_active = TRUE;

CREATE TABLE IF NOT EXISTS notification.notification_preferences (
    id UUID PRIMARY KEY,
    created_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by UUID,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    user_id UUID NOT NULL,
    fcm_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    email_enabled BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT uk_notification_preferences_user_id UNIQUE (user_id)
);
