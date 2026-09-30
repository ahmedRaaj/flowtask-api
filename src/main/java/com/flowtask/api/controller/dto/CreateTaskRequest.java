package com.flowtask.api.controller.dto;

import com.flowtask.api.domain.TaskPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateTaskRequest(
        @NotBlank
        @Size(max = 255)
        String title,
        String description,
        TaskPriority priority,
        LocalDate deadline
) {
}
