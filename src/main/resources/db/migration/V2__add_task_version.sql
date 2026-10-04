-- Optimistic-locking version counter; exposed to clients as the task's ETag.
ALTER TABLE tasks
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
