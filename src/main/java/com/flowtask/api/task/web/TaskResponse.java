package com.flowtask.api.task.web;

import com.flowtask.api.task.TaskPriority;
import com.flowtask.api.task.TaskStatus;

import java.time.Instant;
import java.time.LocalDate;

public record TaskResponse(
        Long id,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        LocalDate deadline,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt,
        boolean overdue
) {
}
