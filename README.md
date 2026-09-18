# FlowTask API

FlowTask is a task-management application for busy professionals who want one reliable place to capture, prioritize, and complete their personal tasks without missing deadlines.

This repository contains the Spring Boot backend API. The initial MVP supports the task lifecycle: create, view, edit, complete or reopen, and permanently delete a task. Authentication, team collaboration, projects, tags, reminders, uploads, and cloud deployment are planned for later iterations.

## Prerequisites

- Java 21
- Maven 3.8+ (the included Maven Wrapper is recommended)

## Run locally

From the repository root:

```bash
./mvnw spring-boot:run
```

The API starts at `http://localhost:8080`.

## Test

```bash
./mvnw test
```

## Available endpoints

| Endpoint | Purpose |
| --- | --- |
| `GET /actuator/health` | Application health check |
| `GET /api/v1/tasks/ping` | Confirms that the versioned task API is running |

Example ping response:

```json
{
  "message": "Ping successful!"
}
