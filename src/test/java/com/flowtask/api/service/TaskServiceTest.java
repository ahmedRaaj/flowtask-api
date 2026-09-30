package com.flowtask.api.service;

import com.flowtask.api.domain.NewTaskDetails;
import com.flowtask.api.domain.Task;
import com.flowtask.api.domain.TaskPriority;
import com.flowtask.api.domain.TaskStatus;
import com.flowtask.api.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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

        Task created = taskService.createTask(new NewTaskDetails("Write project README", null, null, null));

        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(taskCaptor.capture());
        assertThat(created.getTitle()).isEqualTo("Write project README");
        assertThat(taskCaptor.getValue().getStatus()).isEqualTo(TaskStatus.OPEN);
        assertThat(taskCaptor.getValue().getPriority()).isEqualTo(TaskPriority.MEDIUM);
        assertThat(taskCaptor.getValue().getDescription()).isNull();
        assertThat(taskCaptor.getValue().getDeadline()).isNull();
    }

    @Test
    void createsTaskWithOptionalValues() {
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var deadline = LocalDate.of(2026, 10, 1);

        Task created = taskService.createTask(
                new NewTaskDetails("Prepare release", "Review the checklist", TaskPriority.HIGH, deadline));

        assertThat(created.getTitle()).isEqualTo("Prepare release");
        assertThat(created.getDescription()).isEqualTo("Review the checklist");
        assertThat(created.getPriority()).isEqualTo(TaskPriority.HIGH);
        assertThat(created.getDeadline()).isEqualTo(deadline);
        assertThat(created.getStatus()).isEqualTo(TaskStatus.OPEN);
    }

    @Test
    void throwsNotFoundWhenTaskDoesNotExist() {
        when(taskRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.getTaskById(999L))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessage("Task with id 999 was not found");
    }
}
