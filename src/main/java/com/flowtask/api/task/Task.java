package com.flowtask.api.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "tasks")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Task {

    public static final int TITLE_MAX_LENGTH = 255;
    public static final int DESCRIPTION_MAX_LENGTH = 5000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = TITLE_MAX_LENGTH)
    @Column(nullable = false, length = TITLE_MAX_LENGTH)
    private String title;

    @Size(max = DESCRIPTION_MAX_LENGTH)
    @Column(columnDefinition = "text")
    private String description;

    // No setters on this entity: details change only via updateDetails() and status only via
    // complete()/reopen(), which enforce the "completed tasks are immutable until reopened" rule.
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status = TaskStatus.OPEN;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskPriority priority = TaskPriority.MEDIUM;

    private LocalDate deadline;

    @Column(name = "completed_at")
    private Instant completedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public Task(String title) {
        this(title, null, null, null);
    }

    public Task(String title, String description, TaskPriority priority, LocalDate deadline) {
        this.title = normalizeTitle(title);
        this.description = validateDescription(description);
        this.priority = Objects.requireNonNullElse(priority, TaskPriority.MEDIUM);
        this.deadline = deadline;
    }

    /**
     * Replaces all user-editable details in one step. {@code description} and {@code deadline} may be
     * {@code null} to clear them. Every value is validated before any field changes, so a rejected
     * edit leaves the task untouched.
     *
     * @throws TaskNotEditableException if the task is completed and has not been reopened
     */
    public void updateDetails(String title, String description, TaskPriority priority, LocalDate deadline) {
        ensureEditable();
        String normalizedTitle = normalizeTitle(title);
        String validatedDescription = validateDescription(description);
        Objects.requireNonNull(priority, "priority must not be null");

        this.title = normalizedTitle;
        this.description = validatedDescription;
        this.priority = priority;
        this.deadline = deadline;
    }

    /**
     * Marks this task as completed at the given instant. Idempotent: completing an
     * already-completed task keeps its original {@code completedAt}.
     */
    public void complete(Instant completedAt) {
        Objects.requireNonNull(completedAt, "completedAt must not be null");
        if (status == TaskStatus.COMPLETED) {
            return;
        }
        this.status = TaskStatus.COMPLETED;
        this.completedAt = completedAt;
    }

    /**
     * Reopens a completed task so it can be edited again. Idempotent: reopening an
     * open task has no effect.
     */
    public void reopen() {
        if (status == TaskStatus.OPEN) {
            return;
        }
        this.status = TaskStatus.OPEN;
        this.completedAt = null;
    }

    /**
     * Whether this task is still open and its deadline is before {@code today}.
     */
    public boolean isOverdue(LocalDate today) {
        return status == TaskStatus.OPEN && deadline != null && deadline.isBefore(today);
    }

    private void ensureEditable() {
        if (status == TaskStatus.COMPLETED) {
            throw new TaskNotEditableException();
        }
    }

    private static String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        String normalized = title.strip();
        if (normalized.length() > TITLE_MAX_LENGTH) {
            throw new IllegalArgumentException("title must be at most %d characters".formatted(TITLE_MAX_LENGTH));
        }
        return normalized;
    }

    private static String validateDescription(String description) {
        if (description != null && description.length() > DESCRIPTION_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "description must be at most %d characters".formatted(DESCRIPTION_MAX_LENGTH));
        }
        return description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Task task)) return false;
        return id != null && id.equals(task.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Task{id=%s, title='%s', status=%s, priority=%s, deadline=%s}"
                .formatted(id, title, status, priority, deadline);
    }
}
