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

## Database (Docker Compose)

A local PostgreSQL instance is provided via `compose.yaml` (works with OrbStack, Docker Desktop, or any Docker-compatible engine).

1. Copy the example environment file and adjust the values if you like:

   ```bash
   cp .env.example .env
   ```

   `.env` holds your local database credentials and is git-ignored — never commit real credentials or use them for production.

2. Start PostgreSQL:

   ```bash
   docker compose up -d
   ```

   This creates the `flowtask` database, exposes it on `localhost:5432`, persists data in the named volume `flowtask-postgres-data`, and runs a health check (`pg_isready`) so dependent services can wait until the database is ready.

3. Check status:

   ```bash
   docker compose ps
   ```

4. Stop the database (data is preserved in the volume):

   ```bash
   docker compose down
   ```

   To also delete the stored data, add `-v` (`docker compose down -v`).

## Test

```bash
./mvnw test
```

## Project structure

Code is organised by feature rather than by technical layer. Each feature package owns its domain model, service, and persistence; its HTTP adapter lives in a `web` subpackage. Cross-cutting infrastructure lives in `common`.

```
com.flowtask.api
├── common/
│   ├── config/      Shared beans (Clock, JPA auditing)
│   ├── exception/   Base exception types
│   └── web/         Global error handling (RFC 9457 problem details)
└── task/            Task entity, service, command, repository (package-private)
    └── web/         Controller, request/response DTOs, mapper
```

New features (e.g. `project/`) are added as sibling packages following the same shape.

## Available endpoints

| Endpoint | Purpose |
| --- | --- |
| `GET /actuator/health` | Application health check |
| `GET /api/v1/tasks/ping` | Confirms that the versioned task API is running |
| `POST /api/v1/tasks` | Creates a task |
| `GET /api/v1/tasks` | Lists all tasks |
| `GET /api/v1/tasks/{id}` | Retrieves one task |

Create a task by sending a nonblank title of at most 255 characters (surrounding whitespace is trimmed). Description (at most 5,000 characters), priority, and deadline are optional; priority defaults to `MEDIUM`, and new tasks start with `OPEN` status.

```http
POST /api/v1/tasks
Content-Type: application/json
```

```json
{
  "title": "Prepare release",
  "description": "Review the release checklist",
  "priority": "HIGH",
  "deadline": "2026-10-01"
}
```

The API responds with `201 Created`, a `Location` header for the new resource, and its representation:

```json
{
  "id": 1,
  "title": "Prepare release",
  "description": "Review the release checklist",
  "status": "OPEN",
  "priority": "HIGH",
  "deadline": "2026-10-01",
  "completedAt": null,
  "createdAt": "2026-09-27T08:00:00Z",
  "updatedAt": "2026-09-27T08:00:00Z",
  "overdue": false
}
```

`GET /api/v1/tasks` returns all tasks as a JSON array, newest first. `GET /api/v1/tasks/{id}` returns one task.

### Errors

Errors use the [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) `application/problem+json` format. An unknown task ID returns `404 Not Found`; invalid input returns `400 Bad Request`, with validation failures listed per field:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Invalid request content.",
  "instance": "/api/v1/tasks",
  "errors": [
    { "field": "title", "message": "must not be blank" }
  ]
}
```

Example ping response:

```json
{
  "message": "Ping successful!"
}
