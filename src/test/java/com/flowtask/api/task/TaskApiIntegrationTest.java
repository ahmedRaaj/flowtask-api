package com.flowtask.api.task;

import com.flowtask.api.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end task lifecycle over HTTP against a real PostgreSQL database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TaskApiIntegrationTest {

    private static final String UPDATE_JSON = """
            {"title":"Prepare v2 release","priority":"LOW"}
            """;

    @Autowired
    MockMvc mockMvc;

    @Test
    void supportsFullTaskLifecycle() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Prepare release","description":"Checklist","priority":"HIGH","deadline":"2026-10-01"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andReturn();
        long id = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.id")).longValue();
        String taskUrl = "/api/v1/tasks/" + id;

        mockMvc.perform(get(taskUrl))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""));

        // Full replacement: omitted description and deadline are cleared.
        mockMvc.perform(update(taskUrl).header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
                .andExpect(jsonPath("$.title").value("Prepare v2 release"))
                .andExpect(jsonPath("$.priority").value("LOW"))
                .andExpect(jsonPath("$.description").value(nullValue()))
                .andExpect(jsonPath("$.deadline").value(nullValue()))
                .andExpect(jsonPath("$.version").value(1));

        // A second client still holding version 0 must not overwrite the first client's edit.
        mockMvc.perform(update(taskUrl).header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isPreconditionFailed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        // Repeating an identical PUT is idempotent: no new version.
        mockMvc.perform(update(taskUrl).header(HttpHeaders.IF_MATCH, "\"1\""))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""));

        MvcResult completed = mockMvc.perform(post(taskUrl + "/complete").header(HttpHeaders.IF_MATCH, "\"1\""))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"2\""))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").value(notNullValue()))
                .andReturn();
        String completedAt = JsonPath.read(completed.getResponse().getContentAsString(), "$.completedAt");

        // Completing again is a no-op that keeps the original completion time and version.
        mockMvc.perform(post(taskUrl + "/complete"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"2\""))
                .andExpect(jsonPath("$.completedAt").value(completedAt));

        mockMvc.perform(update(taskUrl))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Completed tasks must be reopened before they can be edited"));

        mockMvc.perform(post(taskUrl + "/reopen").header(HttpHeaders.IF_MATCH, "\"2\""))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"3\""))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.completedAt").value(nullValue()));

        mockMvc.perform(put(taskUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Prepare v2 release","priority":"HIGH","deadline":"2020-01-01"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"4-overdue\""))
                .andExpect(jsonPath("$.overdue").value(true))
                .andExpect(jsonPath("$.version").value(4));

        mockMvc.perform(delete(taskUrl).header(HttpHeaders.IF_MATCH, "\"3\""))
                .andExpect(status().isPreconditionFailed());

        mockMvc.perform(delete(taskUrl).header(HttpHeaders.IF_MATCH, "\"4-overdue\""))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(taskUrl)).andExpect(status().isNotFound());
        mockMvc.perform(update(taskUrl)).andExpect(status().isNotFound());
        mockMvc.perform(post(taskUrl + "/complete")).andExpect(status().isNotFound());
        mockMvc.perform(post(taskUrl + "/reopen")).andExpect(status().isNotFound());
        mockMvc.perform(delete(taskUrl))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void completedTaskCanBeDeleted() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Archive notes\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String location = created.getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(location).isNotNull();

        mockMvc.perform(post(location + "/complete")).andExpect(status().isOk());

        mockMvc.perform(delete(location)).andExpect(status().isNoContent());
        mockMvc.perform(get(location)).andExpect(status().isNotFound());
    }

    private static MockHttpServletRequestBuilder update(String taskUrl) {
        return put(taskUrl).contentType(MediaType.APPLICATION_JSON).content(UPDATE_JSON);
    }
}
