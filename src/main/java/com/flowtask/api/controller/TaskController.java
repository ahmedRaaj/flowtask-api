package com.flowtask.api.controller;

import com.flowtask.api.controller.dto.CreateTaskRequest;
import com.flowtask.api.controller.dto.PingResponse;
import com.flowtask.api.controller.dto.TaskResponse;
import com.flowtask.api.controller.mapper.TaskMapper;
import com.flowtask.api.domain.Task;
import com.flowtask.api.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController("taskController")
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService taskService;
    private final TaskMapper taskMapper;

    public TaskController(TaskService taskService, TaskMapper taskMapper) {
        this.taskService = taskService;
        this.taskMapper = taskMapper;
    }

    @PostMapping
    public ResponseEntity<TaskResponse> createTask(@Valid @RequestBody CreateTaskRequest request) {
        Task task = taskService.createTask(taskMapper.toNewTaskDetails(request));
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(task.getId())
                .toUri();
        return ResponseEntity.created(location).body(taskMapper.toResponse(task));
    }

    @GetMapping
    public List<TaskResponse> getAllTasks() {
        return taskService.getAllTasks().stream()
                .map(taskMapper::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public TaskResponse getTaskById(@PathVariable Long id) {
        return taskMapper.toResponse(taskService.getTaskById(id));
    }

    @GetMapping("/ping")
    public PingResponse ping() {
        return new PingResponse("Ping successful!");
    }
}
