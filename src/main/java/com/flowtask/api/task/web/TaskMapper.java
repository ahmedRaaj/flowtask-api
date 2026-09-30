package com.flowtask.api.task.web;

import com.flowtask.api.task.CreateTaskCommand;
import com.flowtask.api.task.Task;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
class TaskMapper {

    private final Clock clock;

    CreateTaskCommand toCommand(CreateTaskRequest request) {
        return new CreateTaskCommand(request.title(), request.description(), request.priority(), request.deadline());
    }

    TaskResponse toResponse(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority(),
                task.getDeadline(),
                task.getCompletedAt(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.isOverdue(LocalDate.now(clock))
        );
    }
}
