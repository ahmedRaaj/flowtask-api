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

Example ping response:

```json
{
  "message": "Ping successful!"
}
