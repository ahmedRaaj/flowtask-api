package com.flowtask.api.task.web;

import com.flowtask.api.task.Task;
import com.flowtask.api.task.TaskPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateTaskRequest(
        @NotBlank @Size(max = Task.TITLE_MAX_LENGTH) String title,
        @Size(max = Task.DESCRIPTION_MAX_LENGTH) String description,
        TaskPriority priority,
        LocalDate deadline
) {
}
