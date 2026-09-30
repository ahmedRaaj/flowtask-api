package com.flowtask.api.controller.dto;

import com.flowtask.api.domain.TaskPriority;
import com.flowtask.api.domain.TaskStatus;

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
