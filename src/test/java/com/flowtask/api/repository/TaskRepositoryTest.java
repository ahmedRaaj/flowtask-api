package com.flowtask.api.repository;

import com.flowtask.api.TestcontainersConfiguration;
import com.flowtask.api.domain.Task;
import com.flowtask.api.domain.TaskPriority;
import com.flowtask.api.domain.TaskStatus;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
class TaskRepositoryTest {

    @Autowired
    TaskRepository taskRepository;

    @Test
    void savesTaskWithDefaultStatusAndPriority() {
        Task task = new Task("Write project README");

        Task saved = taskRepository.saveAndFlush(task);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(TaskStatus.OPEN);
        assertThat(saved.getPriority()).isEqualTo(TaskPriority.MEDIUM);
        assertThat(saved.getDeadline()).isNull();
        assertThat(saved.getCompletedAt()).isNull();
    }

    @Test
    void populatesAuditTimestampsAutomatically() {
        Task task = new Task("Plan sprint");

        Task saved = taskRepository.saveAndFlush(task);

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void updatingTaskBumpsUpdatedAtButNotCreatedAt() {
        Task saved = taskRepository.saveAndFlush(new Task("Refactor service layer"));
        var createdAt = saved.getCreatedAt();

        saved.setDescription("Split into smaller classes");
        Task updated = taskRepository.saveAndFlush(saved);

        assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(createdAt);
    }

    @Test
    void completingAndReopeningTaskTogglesStatusAndCompletedAt() {
        Task saved = taskRepository.saveAndFlush(new Task("Ship release"));

        saved.complete();
        Task completed = taskRepository.saveAndFlush(saved);
        assertThat(completed.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(completed.getCompletedAt()).isNotNull();

        completed.reopen();
        Task reopened = taskRepository.saveAndFlush(completed);
        assertThat(reopened.getStatus()).isEqualTo(TaskStatus.OPEN);
        assertThat(reopened.getCompletedAt()).isNull();
    }

    @Test
    void blankTitleFailsValidation() {
        Task task = new Task(" ");

        assertThatThrownBy(() -> taskRepository.saveAndFlush(task))
                .isInstanceOfAny(ConstraintViolationException.class, DataIntegrityViolationException.class);
    }
}
