package com.flowtask.api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("/api/v1/tasks")
public class TaskController {

    @GetMapping("/ping")
    public String ping() {
        return "ping from the task api";
    }
}
