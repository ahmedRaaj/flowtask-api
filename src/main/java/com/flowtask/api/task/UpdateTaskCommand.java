package com.flowtask.api.task;

import java.time.LocalDate;

/**
 * Full replacement of a task's editable details; {@code null} description or deadline clears the value.
 */
public record UpdateTaskCommand(String title, String description, TaskPriority priority, LocalDate deadline) {
}
