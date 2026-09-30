package com.flowtask.api.task;

import java.time.LocalDate;

public record CreateTaskCommand(String title, String description, TaskPriority priority, LocalDate deadline) {
}
