package com.flowtask.api.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    TaskRepository taskRepository;

    @InjectMocks
    TaskService taskService;

    @Test
    void createsTaskWithDefaultStatusAndPriority() {
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Task created = taskService.createTask(new CreateTaskCommand("Write project README", null, null, null));

        assertThat(created.getTitle()).isEqualTo("Write project README");
        assertThat(created.getStatus()).isEqualTo(TaskStatus.OPEN);
        assertThat(created.getPriority()).isEqualTo(TaskPriority.MEDIUM);
        assertThat(created.getDescription()).isNull();
        assertThat(created.getDeadline()).isNull();
    }

    @Test
    void createsTaskWithOptionalValues() {
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));
        LocalDate deadline = LocalDate.of(2026, 10, 1);

        Task created = taskService.createTask(
                new CreateTaskCommand("Prepare release", "Review the checklist", TaskPriority.HIGH, deadline));

        assertThat(created.getTitle()).isEqualTo("Prepare release");
        assertThat(created.getDescription()).isEqualTo("Review the checklist");
        assertThat(created.getPriority()).isEqualTo(TaskPriority.HIGH);
        assertThat(created.getDeadline()).isEqualTo(deadline);
        assertThat(created.getStatus()).isEqualTo(TaskStatus.OPEN);
    }

    @Test
    void returnsAllTasksNewestFirst() {
        List<Task> tasks = List.of(new Task("Second"), new Task("First"));
        when(taskRepository.findAll(TaskService.DEFAULT_SORT)).thenReturn(tasks);

        assertThat(taskService.getAllTasks()).isEqualTo(tasks);
    }

    @Test
    void returnsTaskById() {
        Task task = new Task("Write project README");
        when(taskRepository.findById(42L)).thenReturn(Optional.of(task));

        assertThat(taskService.getTaskById(42L)).isSameAs(task);
    }

    @Test
    void throwsNotFoundWhenTaskDoesNotExist() {
        when(taskRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.getTaskById(999L))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessage("Task with id 999 was not found");
    }
}
