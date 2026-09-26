package com.flowtask.api.domain;

/**
 * Persisted task status. "Overdue" is intentionally not a stored status here —
 * it is derived from {@code status == OPEN && deadline < today}.
 */
public enum TaskStatus {
    OPEN,
    COMPLETED
}
