package com.flowtask.api.controller;

import com.flowtask.api.controller.mapper.TaskMapper;
import com.flowtask.api.domain.NewTaskDetails;
import com.flowtask.api.domain.Task;
import com.flowtask.api.domain.TaskPriority;
import com.flowtask.api.service.TaskNotFoundException;
import com.flowtask.api.service.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskController.class)
@Import(TaskMapper.class)
class TaskControllerTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-09-27T08:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    TaskService taskService;

    @Test
    void testPing() throws Exception {
        mockMvc.perform(get("/api/v1/tasks/ping"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Ping successful!"));
    }

    @Test
    void createsTaskAndReturnsLocationAndRepresentation() throws Exception {
        Task task = task(42L);
        when(taskService.createTask(any(NewTaskDetails.class))).thenReturn(task);

        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Write project README"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/tasks/42"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.title").value("Write project README"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.deadline").doesNotExist());
    }

    @Test
    void createsTaskWithOptionalFields() throws Exception {
        Task task = task(43L, "Prepare release", "Review the checklist", TaskPriority.HIGH,
                LocalDate.of(2026, 10, 1));
        when(taskService.createTask(any(NewTaskDetails.class))).thenReturn(task);

        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"Prepare release",
                                  "description":"Review the checklist",
                                  "priority":"HIGH",
                                  "deadline":"2026-10-01"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Review the checklist"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.deadline").value("2026-10-01"));
    }

    @Test
    void rejectsBlankTitle() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"   "}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(taskService);
    }

    @Test
    void rejectsTitleLongerThanDatabaseColumn() throws Exception {
        String title = "a".repeat(256);

        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(taskService);
    }

    @Test
    void rejectsUnknownPriority() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Plan sprint","priority":"URGENT"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(taskService);
    }

    @Test
    void returnsAllTasks() throws Exception {
        Task task = task(42L);
        when(taskService.getAllTasks()).thenReturn(List.of(task));

        mockMvc.perform(get("/api/v1/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(42))
                .andExpect(jsonPath("$[0].title").value("Write project README"))
                .andExpect(jsonPath("$[0].status").value("OPEN"));
    }

    @Test
    void returnsTaskById() throws Exception {
        Task task = task(42L);
        when(taskService.getTaskById(42L)).thenReturn(task);

        mockMvc.perform(get("/api/v1/tasks/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.title").value("Write project README"));
    }

    @Test
    void returnsNotFoundForMissingTask() throws Exception {
        when(taskService.getTaskById(999L)).thenThrow(new TaskNotFoundException(999L));

        mockMvc.perform(get("/api/v1/tasks/999"))
                .andExpect(status().isNotFound());
    }

    /**
     * Builds a real, fully-formed {@link Task} rather than mocking the entity:
     * mocking a concrete domain class means every getter must be stubbed by
     * hand and none of the entity's own logic (e.g. {@code isOverdue()}) is
     * actually exercised. The generated id and audit timestamps are the only
     * fields normally assigned by the database/JPA lifecycle, so those are
     * set directly via {@link ReflectionTestUtils}.
     */
    private Task task(Long id) {
        return task(id, "Write project README", null, TaskPriority.MEDIUM, null);
    }

    private Task task(Long id, String title, String description, TaskPriority priority, LocalDate deadline) {
        Task task = Task.create(new NewTaskDetails(title, description, priority, deadline));
        ReflectionTestUtils.setField(task, "id", id);
        ReflectionTestUtils.setField(task, "createdAt", FIXED_INSTANT);
        ReflectionTestUtils.setField(task, "updatedAt", FIXED_INSTANT);
        return task;
    }
}