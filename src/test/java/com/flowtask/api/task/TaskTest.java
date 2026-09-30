package com.flowtask.api.task;

import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 27);
    private static final Instant NOW = Instant.parse("2026-09-27T08:00:00Z");

    @Test
    void appliesDefaultsWhenOptionalFieldsAreOmitted() {
        Task task = new Task("Write project README", null, null, null);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.OPEN);
        assertThat(task.getPriority()).isEqualTo(TaskPriority.MEDIUM);
    }

    @Test
    void stripsSurroundingWhitespaceFromTitle() {
        assertThat(new Task("  Plan sprint  ").getTitle()).isEqualTo("Plan sprint");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    void rejectsBlankTitle(String title) {
        assertThatThrownBy(() -> new Task(title))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("title must not be blank");
    }

    @Test
    void rejectsTitleLongerThanMaxLength() {
        String title = "a".repeat(Task.TITLE_MAX_LENGTH + 1);

        assertThatThrownBy(() -> new Task(title)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDescriptionLongerThanMaxLength() {
        String description = "a".repeat(Task.DESCRIPTION_MAX_LENGTH + 1);

        assertThatThrownBy(() -> new Task("Plan sprint", description, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isOverdueWhenOpenAndDeadlineHasPassed() {
        Task task = new Task("Pay invoice", null, null, TODAY.minusDays(1));

        assertThat(task.isOverdue(TODAY)).isTrue();
    }

    @Test
    void isNotOverdueOnDeadlineDayOrWithoutDeadline() {
        assertThat(new Task("Pay invoice", null, null, TODAY).isOverdue(TODAY)).isFalse();
        assertThat(new Task("Pay invoice").isOverdue(TODAY)).isFalse();
    }

    @Test
    void completedTaskIsNeverOverdue() {
        Task task = new Task("Pay invoice", null, null, TODAY.minusDays(1));
        task.complete(NOW);

        assertThat(task.isOverdue(TODAY)).isFalse();
    }

    @Test
    void completeMarksTaskCompletedAndStampsCompletedAt() {
        Task task = new Task("Ship release");

        task.complete(NOW);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(task.getCompletedAt()).isEqualTo(NOW);
    }

    @Test
    void completingAgainKeepsOriginalCompletedAt() {
        Task task = new Task("Ship release");
        task.complete(NOW);

        task.complete(NOW.plus(Duration.ofHours(1)));

        assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(task.getCompletedAt()).isEqualTo(NOW);
    }

    @Test
    void completeRejectsNullInstant() {
        Task task = new Task("Ship release");

        assertThatThrownBy(() -> task.complete(null)).isInstanceOf(NullPointerException.class);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.OPEN);
    }

    @Test
    void reopenMarksTaskOpenAndClearsCompletedAt() {
        Task task = new Task("Ship release");
        task.complete(NOW);

        task.reopen();

        assertThat(task.getStatus()).isEqualTo(TaskStatus.OPEN);
        assertThat(task.getCompletedAt()).isNull();
    }

    @Test
    void reopeningOpenTaskHasNoEffect() {
        Task task = new Task("Ship release");

        task.reopen();

        assertThat(task.getStatus()).isEqualTo(TaskStatus.OPEN);
        assertThat(task.getCompletedAt()).isNull();
    }

    @ParameterizedTest
    @MethodSource("edits")
    void completedTaskCannotBeEdited(Consumer<Task> edit) {
        Task task = new Task("Ship release", "Initial description", TaskPriority.LOW, TODAY);
        task.complete(NOW);

        assertThatThrownBy(() -> edit.accept(task))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Completed tasks must be reopened before they can be edited");
        assertThat(task.getTitle()).isEqualTo("Ship release");
        assertThat(task.getDescription()).isEqualTo("Initial description");
        assertThat(task.getPriority()).isEqualTo(TaskPriority.LOW);
        assertThat(task.getDeadline()).isEqualTo(TODAY);
    }

    @ParameterizedTest
    @MethodSource("edits")
    void reopenedTaskCanBeEditedAgain(Consumer<Task> edit) {
        Task task = new Task("Ship release");
        task.complete(NOW);
        task.reopen();

        edit.accept(task);
    }

    static Stream<Named<Consumer<Task>>> edits() {
        return Stream.of(
                Named.of("title", task -> task.setTitle("Changed title")),
                Named.of("description", task -> task.setDescription("Changed description")),
                Named.of("priority", task -> task.setPriority(TaskPriority.HIGH)),
                Named.of("deadline", task -> task.setDeadline(TODAY.plusDays(1))));
    }
}
