package com.flowtask.api.controller;

import com.flowtask.api.controller.dto.PingResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("taskController")
@RequestMapping("/api/v1/tasks")
public class TaskController {

    @GetMapping("/ping")
    public PingResponse ping() {
        return new PingResponse("Ping successful!");
    }
}
