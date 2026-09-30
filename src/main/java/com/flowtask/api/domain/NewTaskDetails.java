package com.flowtask.api.domain;

import java.time.LocalDate;

/**
 * Client-supplied fields for creating a task. Deliberately decoupled from any
 * particular web request shape so the domain and service layers never depend
 * on a controller DTO.
 */
public record NewTaskDetails(String title, String description, TaskPriority priority, LocalDate deadline) {
}
