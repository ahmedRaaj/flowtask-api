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

New features (e.g. `project/`) are added as sibling packages following the same shape. Significant design decisions are recorded as ADRs in `docs/adr/`.

## Available endpoints

| Endpoint | Purpose |
| --- | --- |
| `GET /actuator/health` | Application health check |
| `GET /api/v1/tasks/ping` | Confirms that the versioned task API is running |
| `POST /api/v1/tasks` | Creates a task |
| `GET /api/v1/tasks` | Lists all tasks |
| `GET /api/v1/tasks/{id}` | Retrieves one task |
| `PUT /api/v1/tasks/{id}` | Replaces a task's editable details |
| `POST /api/v1/tasks/{id}/complete` | Marks a task completed |
| `POST /api/v1/tasks/{id}/reopen` | Reopens a completed task |
| `DELETE /api/v1/tasks/{id}` | Permanently deletes a task |

The design choices behind these endpoints — PUT instead of PATCH, action endpoints for status changes, idempotency, hard delete, and optimistic locking — are recorded in [ADR 0001](docs/adr/0001-task-update-and-deletion-semantics.md).

### Create a task

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

The API responds with `201 Created`, a `Location` header for the new resource, an `ETag` header, and its representation:

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
  "overdue": false,
  "version": 0
}
```

`GET /api/v1/tasks` returns all tasks as a JSON array, newest first. `GET /api/v1/tasks/{id}` returns one task with its `ETag`.

### Edit a task

`PUT` replaces all editable details at once. `title` and `priority` are required; `description` and `deadline` are cleared when omitted or `null`. Status cannot be changed here — use the complete/reopen actions. Completed tasks must be reopened before they can be edited (`409 Conflict`).

```http
PUT /api/v1/tasks/1
Content-Type: application/json
If-Match: "0"
```

```json
{
  "title": "Prepare v2 release",
  "description": "Review the release checklist",
  "priority": "HIGH",
  "deadline": "2026-10-15"
}
```

The response is `200 OK` with the updated task and a new `ETag` (here `"1"`). `PATCH` is intentionally not supported and returns `405 Method Not Allowed`.

### Complete, reopen, and delete

```http
POST /api/v1/tasks/1/complete
POST /api/v1/tasks/1/reopen
DELETE /api/v1/tasks/1
```

`complete` and `reopen` return `200 OK` with the updated task. Both are idempotent: completing an already completed task keeps its original `completedAt`, and reopening an open task changes nothing. `DELETE` returns `204 No Content`; deletion is permanent, applies to open and completed tasks, and a repeated delete returns `404 Not Found`.

### Concurrency (ETag / If-Match)

Every single-task response carries an `ETag`: the task's `version` (also in the body), with an `-overdue` suffix while the task is overdue, e.g. `"5"` or `"5-overdue"`. Send it back in an optional `If-Match` header on `PUT`, `complete`, `reopen`, or `DELETE` to make the change conditional:

- matching version → the request proceeds (the `-overdue` suffix is ignored for this comparison);
- stale version → `412 Precondition Failed` and nothing changes; reload the task and retry;
- no header or `If-Match: *` → unconditional (last write wins);
- weak, multiple, or malformed tags → `400 Bad Request`.

A write that races with another request between read and commit returns `409 Conflict`. `GET /api/v1/tasks/{id}` with a matching `If-None-Match` returns `304 Not Modified`.

### Errors

Errors use the [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) `application/problem+json` format:

| Status | Meaning |
| --- | --- |
| `400 Bad Request` | Validation failure, malformed JSON, non-numeric ID, or unsupported `If-Match` |
| `404 Not Found` | The task does not exist |
| `405 Method Not Allowed` | Unsupported method, e.g. `PATCH` |
| `409 Conflict` | Editing a completed task, or a concurrent modification |
| `412 Precondition Failed` | `If-Match` does not match the task's current version |

Validation failures are listed per field:

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
