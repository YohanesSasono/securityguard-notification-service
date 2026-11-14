-- Run with the notification schema selected (or configure Flyway's default schema accordingly).
-- The column remains nullable so pending rows created before this release retain their legacy lookup fallback.
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS recipient_email VARCHAR(320);
