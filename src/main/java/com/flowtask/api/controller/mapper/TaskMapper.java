package com.flowtask.api.controller.mapper;

import com.flowtask.api.controller.dto.CreateTaskRequest;
import com.flowtask.api.controller.dto.TaskResponse;
import com.flowtask.api.domain.NewTaskDetails;
import com.flowtask.api.domain.Task;
import org.springframework.stereotype.Component;

/**
 * Owns all conversion between web-facing DTOs and domain types, so the DTOs
 * themselves stay pure data carriers and the domain layer stays unaware of
 * the web layer. Kept as a plain Spring component (not MapStruct) while the
 * mapping is this simple; converting to a {@code @Mapper} interface later is
 * a mechanical change since callers only depend on this class.
 */
@Component
public class TaskMapper {

    public NewTaskDetails toNewTaskDetails(CreateTaskRequest request) {
        return new NewTaskDetails(request.title(), request.description(), request.priority(), request.deadline());
    }

    public TaskResponse toResponse(Task task) {
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
                task.isOverdue()
        );
    }
}
