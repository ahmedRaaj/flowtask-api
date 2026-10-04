package com.flowtask.api.task;

import com.flowtask.api.common.exception.PreconditionFailedException;

public class TaskVersionMismatchException extends PreconditionFailedException {

    public TaskVersionMismatchException(Long id, long expectedVersion, long currentVersion) {
        super("Task with id %d is at version %d, not the expected version %d; reload it and retry"
                .formatted(id, currentVersion, expectedVersion));
    }
}
