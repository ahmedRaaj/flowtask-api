package com.flowtask.api.task;

import com.flowtask.api.common.exception.ConflictException;

public class TaskNotEditableException extends ConflictException {

    public TaskNotEditableException() {
        super("Completed tasks must be reopened before they can be edited");
    }
}
