package com.flowtask.api.task;

import com.flowtask.api.common.exception.NotFoundException;

public class TaskNotFoundException extends NotFoundException {

    public TaskNotFoundException(Long id) {
        super("Task with id %d was not found".formatted(id));
    }
}
