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

## Available endpoints

| Endpoint | Purpose |
| --- | --- |
| `GET /actuator/health` | Application health check |
| `GET /api/v1/tasks/ping` | Confirms that the versioned task API is running |
| `POST /api/v1/tasks` | Creates a task |
| `GET /api/v1/tasks` | Lists all tasks |
| `GET /api/v1/tasks/{id}` | Retrieves one task |

Create a task by sending a nonblank title. Description, priority, and deadline are optional; priority defaults to `MEDIUM`, and new tasks start with `OPEN` status.

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

`GET /api/v1/tasks` returns all tasks as a JSON array. `GET /api/v1/tasks/{id}` returns one task; an unknown ID returns `404 Not Found`. Invalid creation input returns `400 Bad Request`.

Example ping response:

```json
{
  "message": "Ping successful!"
}
