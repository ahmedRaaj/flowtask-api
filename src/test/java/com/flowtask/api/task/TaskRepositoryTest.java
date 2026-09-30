package com.flowtask.api.task;

import com.flowtask.api.MutableClock;
import com.flowtask.api.TestcontainersConfiguration;
import com.flowtask.api.common.config.JpaAuditingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class, TaskRepositoryTest.ClockConfig.class})
class TaskRepositoryTest {

    private static final Instant T0 = Instant.parse("2026-09-27T08:00:00Z");

    @Autowired
    TaskRepository taskRepository;

    @Autowired
    TestEntityManager entityManager;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    MutableClock clock;

    @BeforeEach
    void resetClock() {
        clock.setInstant(T0);
    }

    @Test
    void persistsAndReloadsAllFields() {
        LocalDate deadline = LocalDate.of(2026, 10, 1);

        Task reloaded = entityManager.persistFlushFind(
                new Task("Prepare release", "Review the checklist", TaskPriority.HIGH, deadline));

        assertThat(reloaded.getId()).isNotNull();
        assertThat(reloaded.getTitle()).isEqualTo("Prepare release");
        assertThat(reloaded.getDescription()).isEqualTo("Review the checklist");
        assertThat(reloaded.getPriority()).isEqualTo(TaskPriority.HIGH);
        assertThat(reloaded.getDeadline()).isEqualTo(deadline);
        assertThat(reloaded.getStatus()).isEqualTo(TaskStatus.OPEN);
        assertThat(reloaded.getCompletedAt()).isNull();
    }

    @Test
    void storesEnumsAsStrings() {
        Task task = entityManager.persistAndFlush(new Task("Plan sprint", null, TaskPriority.LOW, null));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, priority FROM tasks WHERE id = ?", task.getId());

        assertThat(row).containsEntry("status", "OPEN").containsEntry("priority", "LOW");
    }

    @Test
    void setsCreatedAtAndUpdatedAtOnInsert() {
        Task reloaded = entityManager.persistFlushFind(new Task("Plan sprint"));

        assertThat(reloaded.getCreatedAt()).isEqualTo(T0);
        assertThat(reloaded.getUpdatedAt()).isEqualTo(T0);
    }

    @Test
    void bumpsUpdatedAtButNotCreatedAtOnUpdate() {
        Task task = entityManager.persistFlushFind(new Task("Refactor service layer"));

        clock.advance(Duration.ofMinutes(5));
        task.updateDetails(task.getTitle(), "Split into smaller classes", task.getPriority(), task.getDeadline());
        Task reloaded = flushAndReload(task);

        assertThat(reloaded.getCreatedAt()).isEqualTo(T0);
        assertThat(reloaded.getUpdatedAt()).isEqualTo(T0.plus(Duration.ofMinutes(5)));
    }

    @Test
    void persistsUpdatedDetails() {
        Task task = entityManager.persistFlushFind(
                new Task("Ship release", "Initial description", TaskPriority.LOW, LocalDate.of(2026, 10, 1)));

        task.updateDetails("Ship v2", null, TaskPriority.HIGH, null);
        Task reloaded = flushAndReload(task);

        assertThat(reloaded.getTitle()).isEqualTo("Ship v2");
        assertThat(reloaded.getDescription()).isNull();
        assertThat(reloaded.getPriority()).isEqualTo(TaskPriority.HIGH);
        assertThat(reloaded.getDeadline()).isNull();
    }

    @Test
    void startsAtVersionZeroAndIncrementsOnEachChange() {
        Task task = entityManager.persistFlushFind(new Task("Ship release"));
        assertThat(task.getVersion()).isZero();

        task.updateDetails("Ship v2", null, TaskPriority.HIGH, null);
        Task updated = flushAndReload(task);
        assertThat(updated.getVersion()).isEqualTo(1);

        updated.complete(T0);
        assertThat(flushAndReload(updated).getVersion()).isEqualTo(2);
    }

    @Test
    void noOpChangesDoNotBumpVersionOrUpdatedAt() {
        Task task = entityManager.persistFlushFind(new Task("Ship release", null, TaskPriority.LOW, null));
        task.complete(T0);
        Task completed = flushAndReload(task);

        clock.advance(Duration.ofMinutes(5));
        completed.complete(T0.plus(Duration.ofMinutes(5)));
        Task reloaded = flushAndReload(completed);

        assertThat(reloaded.getVersion()).isEqualTo(1);
        assertThat(reloaded.getUpdatedAt()).isEqualTo(T0);
        assertThat(reloaded.getCompletedAt()).isEqualTo(T0);

        reloaded.reopen();
        Task reopened = flushAndReload(reloaded);
        reopened.updateDetails(reopened.getTitle(), reopened.getDescription(), reopened.getPriority(),
                reopened.getDeadline());

        assertThat(flushAndReload(reopened).getVersion()).isEqualTo(2);
    }

    @Test
    void rejectsWriteBasedOnStaleVersion() {
        Task task = entityManager.persistFlushFind(new Task("Ship release"));
        jdbcTemplate.update("UPDATE tasks SET version = version + 1 WHERE id = ?", task.getId());

        task.updateDetails("Ship v2", null, TaskPriority.HIGH, null);

        assertThatThrownBy(() -> taskRepository.saveAndFlush(task))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void persistsCompletionState() {
        Task task = entityManager.persistFlushFind(new Task("Ship release"));

        task.complete(T0);
        Task reloaded = flushAndReload(task);

        assertThat(reloaded.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(reloaded.getCompletedAt()).isEqualTo(T0);
    }

    @Test
    void findAllWithDefaultSortReturnsNewestFirst() {
        entityManager.persist(new Task("Oldest"));
        clock.advance(Duration.ofMinutes(1));
        entityManager.persist(new Task("Newest"));
        entityManager.flush();

        assertThat(taskRepository.findAll(TaskService.DEFAULT_SORT))
                .extracting(Task::getTitle)
                .containsExactly("Newest", "Oldest");
    }

    @Test
    void findAllWithDefaultSortBreaksTiesByIdDescending() {
        Long first = entityManager.persist(new Task("First")).getId();
        Long second = entityManager.persist(new Task("Second")).getId();
        entityManager.flush();

        assertThat(taskRepository.findAll(TaskService.DEFAULT_SORT))
                .extracting(Task::getId)
                .containsExactly(second, first);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void databaseRejectsBlankTitle(String title) {
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO tasks (title) VALUES (?)", title))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_tasks_title_not_blank");
    }

    @Test
    void databaseRejectsTitleLongerThanMaxLength() {
        String title = "a".repeat(Task.TITLE_MAX_LENGTH + 1);

        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO tasks (title) VALUES (?)", title))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("value too long");
    }

    @ParameterizedTest
    @CsvSource({"status, DONE", "priority, URGENT"})
    void databaseRejectsUnknownEnumValue(String column, String value) {
        String sql = "INSERT INTO tasks (title, %s) VALUES ('Plan sprint', ?)".formatted(column);

        assertThatThrownBy(() -> jdbcTemplate.update(sql, value))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_tasks_" + column);
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {"OPEN, 2026-09-27T08:00:00Z", "COMPLETED, NULL"})
    void databaseRejectsInconsistentCompletionState(String status, String completedAt) {
        String sql = "INSERT INTO tasks (title, status, completed_at) VALUES ('Ship release', ?, ?::timestamptz)";

        assertThatThrownBy(() -> jdbcTemplate.update(sql, status, completedAt))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_tasks_completed_at");
    }

    private Task flushAndReload(Task task) {
        entityManager.flush();
        entityManager.clear();
        return entityManager.find(Task.class, task.getId());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfig {

        @Bean
        MutableClock clock() {
            return new MutableClock(T0, ZoneOffset.UTC);
        }
    }
}
