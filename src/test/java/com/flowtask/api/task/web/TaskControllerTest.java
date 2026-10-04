package com.flowtask.api.task.web;

import com.flowtask.api.task.CreateTaskCommand;
import com.flowtask.api.task.Task;
import com.flowtask.api.task.TaskNotEditableException;
import com.flowtask.api.task.TaskNotFoundException;
import com.flowtask.api.task.TaskPriority;
import com.flowtask.api.task.TaskService;
import com.flowtask.api.task.TaskVersionMismatchException;
import com.flowtask.api.task.UpdateTaskCommand;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskController.class)
@Import({TaskMapper.class, TaskControllerTest.FixedClockConfig.class})
class TaskControllerTest {

    private static final Instant NOW = Instant.parse("2026-09-27T08:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 27);
    private static final String VALID_UPDATE_JSON = """
            {
              "title": "Ship v2",
              "description": "Updated description",
              "priority": "HIGH",
              "deadline": "2026-10-15"
            }
            """;

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
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
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
                .andExpect(jsonPath("$.overdue").value(false))
                .andExpect(jsonPath("$.version").value(0));
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
        when(taskService.getTaskById(42L)).thenReturn(persisted(42L, 5L, new Task("Write project README")));

        mockMvc.perform(get("/api/v1/tasks/42"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"5\""))
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.title").value("Write project README"))
                .andExpect(jsonPath("$.version").value(5));
    }

    @Test
    void marksOpenTaskWithPastDeadlineAsOverdue() throws Exception {
        Task task = new Task("Pay invoice", null, null, TODAY.minusDays(1));
        when(taskService.getTaskById(42L)).thenReturn(persisted(42L, task));

        mockMvc.perform(get("/api/v1/tasks/42"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0-overdue\""))
                .andExpect(jsonPath("$.overdue").value(true));
    }

    @Test
    void answersConditionalGetWithNotModifiedWhenETagMatches() throws Exception {
        when(taskService.getTaskById(42L)).thenReturn(persisted(42L, 5L, new Task("Write project README")));

        mockMvc.perform(get("/api/v1/tasks/42").header(HttpHeaders.IF_NONE_MATCH, "\"5\""))
                .andExpect(status().isNotModified())
                .andExpect(content().string(""));
    }

    @Test
    void conditionalGetReturnsFreshBodyOnceTaskBecomesOverdue() throws Exception {
        Task task = new Task("Pay invoice", null, null, TODAY.minusDays(1));
        when(taskService.getTaskById(42L)).thenReturn(persisted(42L, 5L, task));

        // "5" was cached before the deadline passed; the task has not been modified since.
        mockMvc.perform(get("/api/v1/tasks/42").header(HttpHeaders.IF_NONE_MATCH, "\"5\""))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"5-overdue\""))
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

    @ParameterizedTest
    @ValueSource(strings = {"GET", "PUT", "DELETE"})
    void rejectsNonNumericId(String method) throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf(method), "/api/v1/tasks/abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_UPDATE_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(taskService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"complete", "reopen"})
    void rejectsNonNumericIdForActions(String action) throws Exception {
        mockMvc.perform(post("/api/v1/tasks/abc/" + action))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(taskService);
    }

    @Test
    void doesNotSupportPartialUpdatesViaPatch() throws Exception {
        mockMvc.perform(patch("/api/v1/tasks/42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Changed\"}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, containsString("PUT")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(taskService);
    }

    @Nested
    class UpdateTask {

        private final UpdateTaskCommand expectedCommand = new UpdateTaskCommand(
                "Ship v2", "Updated description", TaskPriority.HIGH, LocalDate.of(2026, 10, 15));

        @Test
        void replacesDetailsAndReturnsNewETag() throws Exception {
            Task updated = new Task("Ship v2", "Updated description", TaskPriority.HIGH, LocalDate.of(2026, 10, 15));
            when(taskService.updateTask(eq(42L), any(), any())).thenReturn(persisted(42L, 4L, updated));

            putTask(42L, VALID_UPDATE_JSON)
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ETAG, "\"4\""))
                    .andExpect(jsonPath("$.id").value(42))
                    .andExpect(jsonPath("$.title").value("Ship v2"))
                    .andExpect(jsonPath("$.description").value("Updated description"))
                    .andExpect(jsonPath("$.priority").value("HIGH"))
                    .andExpect(jsonPath("$.deadline").value("2026-10-15"))
                    .andExpect(jsonPath("$.status").value("OPEN"))
                    .andExpect(jsonPath("$.version").value(4));

            verify(taskService).updateTask(42L, expectedCommand, null);
        }

        @Test
        void clearsOptionalFieldsThatAreOmitted() throws Exception {
            when(taskService.updateTask(eq(42L), any(), any()))
                    .thenReturn(persisted(42L, new Task("Ship v2", null, TaskPriority.LOW, null)));

            putTask(42L, """
                    {"title":"Ship v2","priority":"LOW"}
                    """)
                    .andExpect(status().isOk());

            verify(taskService).updateTask(42L, new UpdateTaskCommand("Ship v2", null, TaskPriority.LOW, null), null);
        }

        @Test
        void passesIfMatchVersionToService() throws Exception {
            when(taskService.updateTask(eq(42L), any(), any())).thenReturn(persisted(42L, 4L, new Task("Ship v2")));

            putTask(42L, VALID_UPDATE_JSON, "\"3\"").andExpect(status().isOk());

            verify(taskService).updateTask(42L, expectedCommand, 3L);
        }

        @Test
        void ignoresOverdueMarkerWhenMatchingVersion() throws Exception {
            when(taskService.updateTask(eq(42L), any(), any())).thenReturn(persisted(42L, 4L, new Task("Ship v2")));

            putTask(42L, VALID_UPDATE_JSON, "\"3-overdue\"").andExpect(status().isOk());

            verify(taskService).updateTask(42L, expectedCommand, 3L);
        }

        @Test
        void treatsWildcardIfMatchAsUnconditional() throws Exception {
            when(taskService.updateTask(eq(42L), any(), any())).thenReturn(persisted(42L, new Task("Ship v2")));

            putTask(42L, VALID_UPDATE_JSON, "*").andExpect(status().isOk());

            verify(taskService).updateTask(42L, expectedCommand, null);
        }

        @ParameterizedTest
        @ValueSource(strings = {"W/\"3\"", "\"1\", \"2\"", "3", "\"abc\"", "\"-1\"", "\"3-late\"",
                "\"3-overdue-overdue\"", "\"99999999999999999999\""})
        void rejectsUnsupportedIfMatch(String ifMatch) throws Exception {
            putTask(42L, VALID_UPDATE_JSON, ifMatch)
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.detail").value(TaskETags.INVALID_IF_MATCH_DETAIL));

            verifyNoInteractions(taskService);
        }

        @ParameterizedTest
        @MethodSource("com.flowtask.api.task.web.TaskControllerTest#invalidUpdateBodies")
        void rejectsInvalidBody(String json, String field) throws Exception {
            putTask(42L, json)
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.errors[0].field").value(field));

            verifyNoInteractions(taskService);
        }

        @Test
        void rejectsMalformedJson() throws Exception {
            putTask(42L, "{\"title\":")
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

            verifyNoInteractions(taskService);
        }

        @Test
        void returnsConflictWhenTaskIsCompleted() throws Exception {
            when(taskService.updateTask(eq(42L), any(), any())).thenThrow(new TaskNotEditableException());

            putTask(42L, VALID_UPDATE_JSON)
                    .andExpect(status().isConflict())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.detail").value("Completed tasks must be reopened before they can be edited"));
        }

        @Test
        void returnsPreconditionFailedForStaleVersion() throws Exception {
            when(taskService.updateTask(eq(42L), any(), any()))
                    .thenThrow(new TaskVersionMismatchException(42L, 2L, 3L));

            putTask(42L, VALID_UPDATE_JSON, "\"2\"")
                    .andExpect(status().isPreconditionFailed())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(412))
                    .andExpect(jsonPath("$.detail")
                            .value("Task with id 42 is at version 3, not the expected version 2; reload it and retry"));
        }

        @Test
        void returnsConflictOnConcurrentModification() throws Exception {
            when(taskService.updateTask(eq(42L), any(), any()))
                    .thenThrow(new ObjectOptimisticLockingFailureException(Task.class, 42L));

            putTask(42L, VALID_UPDATE_JSON)
                    .andExpect(status().isConflict())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.detail")
                            .value("The resource was modified concurrently by another request; reload it and retry"));
        }

        @Test
        void returnsNotFoundForMissingTask() throws Exception {
            when(taskService.updateTask(eq(999L), any(), any())).thenThrow(new TaskNotFoundException(999L));

            putTask(999L, VALID_UPDATE_JSON)
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.detail").value("Task with id 999 was not found"));
        }
    }

    @Nested
    class CompleteTask {

        @Test
        void completesTask() throws Exception {
            Task task = new Task("Ship release");
            task.complete(NOW);
            when(taskService.completeTask(42L, null)).thenReturn(persisted(42L, 1L, task));

            mockMvc.perform(post("/api/v1/tasks/42/complete"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.completedAt").value("2026-09-27T08:00:00Z"))
                    .andExpect(jsonPath("$.overdue").value(false));
        }

        @Test
        void passesIfMatchVersionToService() throws Exception {
            Task task = new Task("Ship release");
            task.complete(NOW);
            when(taskService.completeTask(42L, 0L)).thenReturn(persisted(42L, 1L, task));

            mockMvc.perform(post("/api/v1/tasks/42/complete").header(HttpHeaders.IF_MATCH, "\"0\""))
                    .andExpect(status().isOk());

            verify(taskService).completeTask(42L, 0L);
        }

        @Test
        void returnsPreconditionFailedForStaleVersion() throws Exception {
            when(taskService.completeTask(42L, 0L)).thenThrow(new TaskVersionMismatchException(42L, 0L, 1L));

            mockMvc.perform(post("/api/v1/tasks/42/complete").header(HttpHeaders.IF_MATCH, "\"0\""))
                    .andExpect(status().isPreconditionFailed())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @Test
        void returnsNotFoundForMissingTask() throws Exception {
            when(taskService.completeTask(999L, null)).thenThrow(new TaskNotFoundException(999L));

            mockMvc.perform(post("/api/v1/tasks/999/complete"))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.detail").value("Task with id 999 was not found"));
        }
    }

    @Nested
    class ReopenTask {

        @Test
        void reopensTask() throws Exception {
            when(taskService.reopenTask(42L, null)).thenReturn(persisted(42L, 2L, new Task("Ship release")));

            mockMvc.perform(post("/api/v1/tasks/42/reopen"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ETAG, "\"2\""))
                    .andExpect(jsonPath("$.status").value("OPEN"))
                    .andExpect(jsonPath("$.completedAt").value(nullValue()));
        }

        @Test
        void returnsPreconditionFailedForStaleVersion() throws Exception {
            when(taskService.reopenTask(42L, 1L)).thenThrow(new TaskVersionMismatchException(42L, 1L, 2L));

            mockMvc.perform(post("/api/v1/tasks/42/reopen").header(HttpHeaders.IF_MATCH, "\"1\""))
                    .andExpect(status().isPreconditionFailed())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @Test
        void returnsNotFoundForMissingTask() throws Exception {
            when(taskService.reopenTask(999L, null)).thenThrow(new TaskNotFoundException(999L));

            mockMvc.perform(post("/api/v1/tasks/999/reopen"))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
    }

    @Nested
    class DeleteTask {

        @Test
        void deletesTaskAndReturnsNoContent() throws Exception {
            mockMvc.perform(delete("/api/v1/tasks/42"))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));

            verify(taskService).deleteTask(42L, null);
        }

        @Test
        void passesIfMatchVersionToService() throws Exception {
            mockMvc.perform(delete("/api/v1/tasks/42").header(HttpHeaders.IF_MATCH, "\"7\""))
                    .andExpect(status().isNoContent());

            verify(taskService).deleteTask(42L, 7L);
        }

        @Test
        void returnsPreconditionFailedForStaleVersion() throws Exception {
            doThrow(new TaskVersionMismatchException(42L, 6L, 7L)).when(taskService).deleteTask(42L, 6L);

            mockMvc.perform(delete("/api/v1/tasks/42").header(HttpHeaders.IF_MATCH, "\"6\""))
                    .andExpect(status().isPreconditionFailed())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @Test
        void returnsNotFoundForMissingTask() throws Exception {
            doThrow(new TaskNotFoundException(999L)).when(taskService).deleteTask(999L, null);

            mockMvc.perform(delete("/api/v1/tasks/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.detail").value("Task with id 999 was not found"));
        }
    }

    static Stream<Arguments> invalidUpdateBodies() {
        return Stream.of(
                Arguments.of(Named.of("missing title", "{\"priority\":\"HIGH\"}"), "title"),
                Arguments.of(Named.of("blank title", "{\"title\":\"  \",\"priority\":\"HIGH\"}"), "title"),
                Arguments.of(Named.of("too long title", "{\"title\":\"%s\",\"priority\":\"HIGH\"}"
                        .formatted("a".repeat(Task.TITLE_MAX_LENGTH + 1))), "title"),
                Arguments.of(Named.of("too long description",
                        "{\"title\":\"Ship v2\",\"description\":\"%s\",\"priority\":\"HIGH\"}"
                                .formatted("a".repeat(Task.DESCRIPTION_MAX_LENGTH + 1))), "description"),
                Arguments.of(Named.of("missing priority", "{\"title\":\"Ship v2\"}"), "priority"),
                Arguments.of(Named.of("null priority", "{\"title\":\"Ship v2\",\"priority\":null}"), "priority"));
    }

    private ResultActions postTask(String json) throws Exception {
        return mockMvc.perform(post("/api/v1/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions putTask(long id, String json) throws Exception {
        return mockMvc.perform(put("/api/v1/tasks/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions putTask(long id, String json, String ifMatch) throws Exception {
        return mockMvc.perform(put("/api/v1/tasks/{id}", id)
                .header(HttpHeaders.IF_MATCH, ifMatch)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    /** Simulates the fields that JPA assigns on persist. */
    private static Task persisted(Long id, Task task) {
        return persisted(id, 0L, task);
    }

    private static Task persisted(Long id, long version, Task task) {
        ReflectionTestUtils.setField(task, "id", id);
        ReflectionTestUtils.setField(task, "version", version);
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
