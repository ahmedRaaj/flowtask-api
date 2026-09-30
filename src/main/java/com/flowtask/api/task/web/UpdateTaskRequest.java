package com.flowtask.api.task.web;

import com.flowtask.api.task.Task;
import com.flowtask.api.task.TaskPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Full replacement of a task's editable details (PUT semantics). Omitting {@code description} or
 * {@code deadline}, or sending {@code null}, clears the value. Status is not editable here; use the
 * complete/reopen actions instead.
 */
public record UpdateTaskRequest(
        @NotBlank @Size(max = Task.TITLE_MAX_LENGTH) String title,
        @Size(max = Task.DESCRIPTION_MAX_LENGTH) String description,
        @NotNull TaskPriority priority,
        LocalDate deadline
) {
}
