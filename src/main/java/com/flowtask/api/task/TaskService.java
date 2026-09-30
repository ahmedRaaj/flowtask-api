package com.flowtask.api.task;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * Task use cases. Mutating operations accept an optional {@code expectedVersion}: when non-null it must
 * equal the task's current version, otherwise {@link TaskVersionMismatchException} is thrown before
 * anything changes. Checks run in order: existence (404), version (412), business rules (409).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskService {

    static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final TaskRepository taskRepository;
    private final Clock clock;

    @Transactional
    public Task createTask(CreateTaskCommand command) {
        Task task = new Task(command.title(), command.description(), command.priority(), command.deadline());
        return taskRepository.save(task);
    }

    public List<Task> getAllTasks() {
        return taskRepository.findAll(DEFAULT_SORT);
    }

    public Task getTaskById(Long id) {
        return taskRepository.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
    }

    @Transactional
    public Task updateTask(Long id, UpdateTaskCommand command, Long expectedVersion) {
        Task task = getTaskForWrite(id, expectedVersion);
        task.updateDetails(command.title(), command.description(), command.priority(), command.deadline());
        return task;
    }

    @Transactional
    public Task completeTask(Long id, Long expectedVersion) {
        Task task = getTaskForWrite(id, expectedVersion);
        task.complete(clock.instant());
        return task;
    }

    @Transactional
    public Task reopenTask(Long id, Long expectedVersion) {
        Task task = getTaskForWrite(id, expectedVersion);
        task.reopen();
        return task;
    }

    @Transactional
    public void deleteTask(Long id, Long expectedVersion) {
        taskRepository.delete(getTaskForWrite(id, expectedVersion));
    }

    private Task getTaskForWrite(Long id, Long expectedVersion) {
        Task task = getTaskById(id);
        if (expectedVersion != null && expectedVersion != task.getVersion()) {
            throw new TaskVersionMismatchException(id, expectedVersion, task.getVersion());
        }
        return task;
    }
}
