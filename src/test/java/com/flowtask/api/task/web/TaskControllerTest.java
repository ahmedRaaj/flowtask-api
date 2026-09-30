package com.flowtask.api.task.web;

import com.flowtask.api.task.CreateTaskCommand;
import com.flowtask.api.task.Task;
import com.flowtask.api.task.TaskNotFoundException;
import com.flowtask.api.task.TaskPriority;
import com.flowtask.api.task.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskController.class)
@Import({TaskMapper.class, TaskControllerTest.FixedClockConfig.class})
class TaskControllerTest {

    private static final Instant NOW = Instant.parse("2026-09-27T08:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 27);

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    TaskService taskService;

    @Test
    void ping() throws Exception {
        mockMvc.perform(get("/api/v1/tasks/ping"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Ping successful!"));
    }

    @Test
    void createsTaskWithDefaults() throws Exception {
        when(taskService.createTask(any())).thenReturn(persisted(42L, new Task("Write project README")));

        postTask("""
                {"title":"Write project README"}
                """)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/tasks/42"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.title").value("Write project README"))
                .andExpect(jsonPath("$.description").value(nullValue()))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.deadline").value(nullValue()))
                .andExpect(jsonPath("$.completedAt").value(nullValue()))
                .andExpect(jsonPath("$.createdAt").value("2026-09-27T08:00:00Z"))
                .andExpect(jsonPath("$.updatedAt").value("2026-09-27T08:00:00Z"))
                .andExpect(jsonPath("$.overdue").value(false));
    }

    @Test
    void createsTaskWithOptionalFields() throws Exception {
        LocalDate deadline = LocalDate.of(2026, 10, 1);
        when(taskService.createTask(any())).thenReturn(persisted(43L,
                new Task("Prepare release", "Review the checklist", TaskPriority.HIGH, deadline)));

        postTask("""
                {
                  "title": "Prepare release",
                  "description": "Review the checklist",
                  "priority": "HIGH",
                  "deadline": "2026-10-01"
                }
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Review the checklist"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.deadline").value("2026-10-01"));

        verify(taskService).createTask(
                new CreateTaskCommand("Prepare release", "Review the checklist", TaskPriority.HIGH, deadline));
    }

    @Test
    void rejectsMissingTitle() throws Exception {
        postTask("{}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[0].field").value("title"));

        verifyNoInteractions(taskService);
    }

    @Test
    void rejectsBlankTitle() throws Exception {
        postTask("""
                {"title":"   "}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"));

        verifyNoInteractions(taskService);
    }

    @Test
    void rejectsTitleLongerThanMaxLength() throws Exception {
        String title = "a".repeat(Task.TITLE_MAX_LENGTH + 1);

        postTask("{\"title\":\"%s\"}".formatted(title))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"));

        verifyNoInteractions(taskService);
    }

    @Test
    void rejectsDescriptionLongerThanMaxLength() throws Exception {
        String description = "a".repeat(Task.DESCRIPTION_MAX_LENGTH + 1);

        postTask("{\"title\":\"Plan sprint\",\"description\":\"%s\"}".formatted(description))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("description"));

        verifyNoInteractions(taskService);
    }

    @Test
    void rejectsUnknownPriority() throws Exception {
        postTask("""
                {"title":"Plan sprint","priority":"URGENT"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(taskService);
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        postTask("{\"title\":")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(taskService);
    }

    @Test
    void returnsAllTasks() throws Exception {
        when(taskService.getAllTasks()).thenReturn(List.of(
                persisted(2L, new Task("Second")),
                persisted(1L, new Task("First"))));

        mockMvc.perform(get("/api/v1/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[1].id").value(1));
    }

    @Test
    void returnsTaskById() throws Exception {
        when(taskService.getTaskById(42L)).thenReturn(persisted(42L, new Task("Write project README")));

        mockMvc.perform(get("/api/v1/tasks/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.title").value("Write project README"));
    }

    @Test
    void marksOpenTaskWithPastDeadlineAsOverdue() throws Exception {
        Task task = new Task("Pay invoice", null, null, TODAY.minusDays(1));
        when(taskService.getTaskById(42L)).thenReturn(persisted(42L, task));

        mockMvc.perform(get("/api/v1/tasks/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overdue").value(true));
    }

    @Test
    void returnsProblemDetailForMissingTask() throws Exception {
        when(taskService.getTaskById(999L)).thenThrow(new TaskNotFoundException(999L));

        mockMvc.perform(get("/api/v1/tasks/999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Task with id 999 was not found"));
    }

    private ResultActions postTask(String json) throws Exception {
        return mockMvc.perform(post("/api/v1/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    /** Simulates the fields that JPA assigns on persist. */
    private static Task persisted(Long id, Task task) {
        ReflectionTestUtils.setField(task, "id", id);
        ReflectionTestUtils.setField(task, "createdAt", NOW);
        ReflectionTestUtils.setField(task, "updatedAt", NOW);
        return task;
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
