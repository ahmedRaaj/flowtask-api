package com.flowtask.api.task.web;

import com.flowtask.api.task.Task;
import com.flowtask.api.task.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Task resource. Single-task responses carry an {@code ETag} (usable for conditional GETs); mutating
 * requests accept an optional {@code If-Match} to guard against lost updates.
 */
@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final TaskMapper taskMapper;

    @PostMapping
    public ResponseEntity<TaskResponse> createTask(@Valid @RequestBody CreateTaskRequest request) {
        Task task = taskService.createTask(taskMapper.toCommand(request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(task.getId())
                .toUri();
        TaskResponse body = taskMapper.toResponse(task);
        return ResponseEntity.created(location)
                .eTag(TaskETags.of(body))
                .body(body);
    }

    @GetMapping
    public List<TaskResponse> getAllTasks() {
        return taskService.getAllTasks().stream()
                .map(taskMapper::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> getTaskById(@PathVariable Long id) {
        return okWithETag(taskService.getTaskById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TaskResponse> updateTask(
            @PathVariable Long id,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody UpdateTaskRequest request) {
        Task task = taskService.updateTask(id, taskMapper.toCommand(request), TaskETags.expectedVersion(ifMatch));
        return okWithETag(task);
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<TaskResponse> completeTask(
            @PathVariable Long id,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return okWithETag(taskService.completeTask(id, TaskETags.expectedVersion(ifMatch)));
    }

    @PostMapping("/{id}/reopen")
    public ResponseEntity<TaskResponse> reopenTask(
            @PathVariable Long id,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return okWithETag(taskService.reopenTask(id, TaskETags.expectedVersion(ifMatch)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(
            @PathVariable Long id,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        taskService.deleteTask(id, TaskETags.expectedVersion(ifMatch));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/ping")
    public PingResponse ping() {
        return new PingResponse("Ping successful!");
    }

    private ResponseEntity<TaskResponse> okWithETag(Task task) {
        TaskResponse body = taskMapper.toResponse(task);
        return ResponseEntity.ok()
                .eTag(TaskETags.of(body))
                .body(body);
    }
}
