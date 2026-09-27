package com.flowtask.api.repository;

import com.flowtask.api.TestcontainersConfiguration;
import com.flowtask.api.domain.Task;
import com.flowtask.api.domain.TaskPriority;
import com.flowtask.api.domain.TaskStatus;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
class TaskRepositoryTest {

    @Autowired
    TaskRepository taskRepository;

    @Autowired
    EntityManager entityManager;

    @Autowired
    JdbcTemplate jdbcTemplate;

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
    void updatingTaskBumpsUpdatedAtButNotCreatedAt() throws InterruptedException {
        Task saved = taskRepository.saveAndFlush(new Task("Refactor service layer"));
        var createdAt = saved.getCreatedAt();

        Thread.sleep(10);
        saved.setDescription("Split into smaller classes");
        taskRepository.saveAndFlush(saved);
        entityManager.clear();
        Task updated = taskRepository.findById(saved.getId()).orElseThrow();

        assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
        assertThat(updated.getUpdatedAt()).isAfter(createdAt);
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
    void completedTaskCannotBeEditedUntilReopened() {
        Task saved = taskRepository.saveAndFlush(new Task("Ship release"));
        saved.setDescription("Initial description");
        saved.setPriority(TaskPriority.LOW);
        saved.setDeadline(LocalDate.of(2026, 10, 1));
        saved.complete();
        taskRepository.saveAndFlush(saved);

        assertThatThrownBy(() -> saved.setTitle("Changed title"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> saved.setDescription("Changed description"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> saved.setPriority(TaskPriority.HIGH))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> saved.setDeadline(LocalDate.of(2026, 11, 1)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(saved.getTitle()).isEqualTo("Ship release");
        assertThat(saved.getDescription()).isEqualTo("Initial description");
        assertThat(saved.getPriority()).isEqualTo(TaskPriority.LOW);
        assertThat(saved.getDeadline()).isEqualTo(LocalDate.of(2026, 10, 1));

        saved.reopen();
        saved.setTitle("Updated release");
        saved.setDescription("Updated description");
        saved.setPriority(TaskPriority.HIGH);
        saved.setDeadline(LocalDate.of(2026, 11, 1));
        Task reopened = taskRepository.saveAndFlush(saved);

        assertThat(reopened.getTitle()).isEqualTo("Updated release");
        assertThat(reopened.getDescription()).isEqualTo("Updated description");
        assertThat(reopened.getPriority()).isEqualTo(TaskPriority.HIGH);
        assertThat(reopened.getDeadline()).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    void blankTitleFailsValidation() {
        Task task = new Task(" ");

        assertThatThrownBy(() -> taskRepository.saveAndFlush(task))
                .isInstanceOfAny(ConstraintViolationException.class, DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsBlankTitleWhenInsertedDirectly() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO tasks (title) VALUES (?)", "   "))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsEmptyTitleWhenInsertedDirectly() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO tasks (title) VALUES (?)", ""))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
