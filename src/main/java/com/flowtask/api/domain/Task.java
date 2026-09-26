package com.flowtask.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "tasks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    @Setter
    private String title;

    @Column(columnDefinition = "text")
    @Setter
    private String description;

    // No setter: status only changes via complete()/reopen() to enforce
    // the "completed tasks are immutable until reopened" business rule.
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status = TaskStatus.OPEN;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Setter
    private TaskPriority priority = TaskPriority.MEDIUM;

    @Setter
    private LocalDate deadline;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Task(String title) {
        this.title = title;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Marks this task as completed, stamping {@code completedAt}.
     */
    public void complete() {
        this.status = TaskStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    /**
     * Reopens a completed task, clearing {@code completedAt} so it can be edited again.
     */
    public void reopen() {
        this.status = TaskStatus.OPEN;
        this.completedAt = null;
    }

    /**
     * Whether this task is overdue: still open and its deadline has passed.
     * This is a computed, non-persisted property.
     */
    public boolean isOverdue() {
        return status == TaskStatus.OPEN && deadline != null && deadline.isBefore(LocalDate.now());
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
