package com.flowtask.api.task;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-27T08:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 27);
    private static final long TASK_ID = 42L;
    private static final long MISSING_ID = 999L;
    private static final long CURRENT_VERSION = 3L;

    @Mock
    TaskRepository taskRepository;

    TaskService taskService;

    @BeforeEach
    void setUp() {
        taskService = new TaskService(taskRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

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
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

        assertThat(taskService.getTaskById(TASK_ID)).isSameAs(task);
    }

    @Test
    void throwsNotFoundWhenTaskDoesNotExist() {
        when(taskRepository.findById(MISSING_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.getTaskById(MISSING_ID))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessage("Task with id 999 was not found");
    }

    @Nested
    class UpdateTask {

        private final UpdateTaskCommand command =
                new UpdateTaskCommand("Ship v2", "Updated description", TaskPriority.HIGH, TODAY.plusDays(7));

        @Test
        void replacesEditableDetails() {
            Task task = existingOpenTask();

            Task updated = taskService.updateTask(TASK_ID, command, null);

            assertThat(updated).isSameAs(task);
            assertThat(updated.getTitle()).isEqualTo("Ship v2");
            assertThat(updated.getDescription()).isEqualTo("Updated description");
            assertThat(updated.getPriority()).isEqualTo(TaskPriority.HIGH);
            assertThat(updated.getDeadline()).isEqualTo(TODAY.plusDays(7));
        }

        @Test
        void succeedsWhenExpectedVersionMatches() {
            existingOpenTask();

            Task updated = taskService.updateTask(TASK_ID, command, CURRENT_VERSION);

            assertThat(updated.getTitle()).isEqualTo("Ship v2");
        }

        @Test
        void rejectsStaleVersionWithoutChangingTask() {
            Task task = existingOpenTask();

            assertThatThrownBy(() -> taskService.updateTask(TASK_ID, command, CURRENT_VERSION - 1))
                    .isInstanceOf(TaskVersionMismatchException.class)
                    .hasMessage("Task with id 42 is at version 3, not the expected version 2; reload it and retry");
            assertThat(task.getTitle()).isEqualTo("Ship release");
        }

        @Test
        void rejectsEditingCompletedTask() {
            Task task = existingCompletedTask();

            assertThatThrownBy(() -> taskService.updateTask(TASK_ID, command, null))
                    .isInstanceOf(TaskNotEditableException.class);
            assertThat(task.getTitle()).isEqualTo("Ship release");
        }

        @Test
        void checksVersionBeforeBusinessRules() {
            existingCompletedTask();

            assertThatThrownBy(() -> taskService.updateTask(TASK_ID, command, CURRENT_VERSION + 1))
                    .isInstanceOf(TaskVersionMismatchException.class);
        }

        @Test
        void throwsNotFoundForMissingTask() {
            when(taskRepository.findById(MISSING_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> taskService.updateTask(MISSING_ID, command, null))
                    .isInstanceOf(TaskNotFoundException.class);
        }
    }

    @Nested
    class CompleteTask {

        @Test
        void completesTaskAtCurrentClockInstant() {
            existingOpenTask();

            Task completed = taskService.completeTask(TASK_ID, null);

            assertThat(completed.getStatus()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(completed.getCompletedAt()).isEqualTo(NOW);
        }

        @Test
        void isIdempotentAndKeepsOriginalCompletionTime() {
            Task task = existingOpenTask();
            task.complete(NOW.minus(Duration.ofDays(1)));

            Task completed = taskService.completeTask(TASK_ID, null);

            assertThat(completed.getStatus()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(completed.getCompletedAt()).isEqualTo(NOW.minus(Duration.ofDays(1)));
        }

        @Test
        void rejectsStaleVersionWithoutCompleting() {
            Task task = existingOpenTask();

            assertThatThrownBy(() -> taskService.completeTask(TASK_ID, CURRENT_VERSION - 1))
                    .isInstanceOf(TaskVersionMismatchException.class);
            assertThat(task.getStatus()).isEqualTo(TaskStatus.OPEN);
        }

        @Test
        void throwsNotFoundForMissingTask() {
            when(taskRepository.findById(MISSING_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> taskService.completeTask(MISSING_ID, null))
                    .isInstanceOf(TaskNotFoundException.class);
        }
    }

    @Nested
    class ReopenTask {

        @Test
        void reopensCompletedTask() {
            existingCompletedTask();

            Task reopened = taskService.reopenTask(TASK_ID, CURRENT_VERSION);

            assertThat(reopened.getStatus()).isEqualTo(TaskStatus.OPEN);
            assertThat(reopened.getCompletedAt()).isNull();
        }

        @Test
        void isIdempotentForOpenTask() {
            existingOpenTask();

            Task reopened = taskService.reopenTask(TASK_ID, null);

            assertThat(reopened.getStatus()).isEqualTo(TaskStatus.OPEN);
        }

        @Test
        void rejectsStaleVersionWithoutReopening() {
            Task task = existingCompletedTask();

            assertThatThrownBy(() -> taskService.reopenTask(TASK_ID, CURRENT_VERSION - 1))
                    .isInstanceOf(TaskVersionMismatchException.class);
            assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        }

        @Test
        void throwsNotFoundForMissingTask() {
            when(taskRepository.findById(MISSING_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> taskService.reopenTask(MISSING_ID, null))
                    .isInstanceOf(TaskNotFoundException.class);
        }
    }

    @Nested
    class DeleteTask {

        @Test
        void deletesExistingTask() {
            Task task = existingOpenTask();

            taskService.deleteTask(TASK_ID, CURRENT_VERSION);

            verify(taskRepository).delete(task);
        }

        @Test
        void deletesCompletedTask() {
            Task task = existingCompletedTask();

            taskService.deleteTask(TASK_ID, null);

            verify(taskRepository).delete(task);
        }

        @Test
        void rejectsStaleVersionWithoutDeleting() {
            existingOpenTask();

            assertThatThrownBy(() -> taskService.deleteTask(TASK_ID, CURRENT_VERSION - 1))
                    .isInstanceOf(TaskVersionMismatchException.class);
            verify(taskRepository, never()).delete(any());
        }

        @Test
        void throwsNotFoundForMissingTask() {
            when(taskRepository.findById(MISSING_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> taskService.deleteTask(MISSING_ID, null))
                    .isInstanceOf(TaskNotFoundException.class)
                    .hasMessage("Task with id 999 was not found");
            verify(taskRepository, never()).delete(any());
        }
    }

    private Task existingOpenTask() {
        Task task = new Task("Ship release", "Initial description", TaskPriority.LOW, TODAY);
        ReflectionTestUtils.setField(task, "id", TASK_ID);
        ReflectionTestUtils.setField(task, "version", CURRENT_VERSION);
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        return task;
    }

    private Task existingCompletedTask() {
        Task task = existingOpenTask();
        task.complete(NOW.minus(Duration.ofHours(1)));
        return task;
    }
}
