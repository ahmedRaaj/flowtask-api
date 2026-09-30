# ADR 0001: Task update, state-transition, and deletion semantics

- **Status:** Accepted
- **Date:** 2026-09-30
- **Scope:** `/api/v1/tasks` (task T09)

## Context

The MVP must let a user edit a task, mark it completed, reopen it, and delete it
([product brief](../product.md)). The domain has these rules:

- Editable details are `title` (required), `description`, `priority` (required), and `deadline`.
- `status` is either `OPEN` or `COMPLETED`, and `completedAt` is set exactly when the status is `COMPLETED`.
- A completed task cannot be edited until it is reopened.
- Deletion is permanent. Authentication and roles are out of scope for now.

Before this change the `Task` entity had a public setter for each field. That allowed arbitrary
single-field changes, and a rejected edit could leave a task partially updated. There was no protection
against two clients overwriting each other's changes. A domain `IllegalStateException` would have
been returned as HTTP 500.

## Decisions

### 1. Edit details with `PUT`; do not offer `PATCH`

`PUT /api/v1/tasks/{id}` replaces **all** editable details in one request:

| Field | PUT rule |
|---|---|
| `title` | Required and not blank. Surrounding whitespace is trimmed. At most 255 characters. |
| `priority` | Required. It is not defaulted, so a client that forgets it cannot silently downgrade a `HIGH` task. |
| `description` | Optional. If omitted or `null`, the stored value is **cleared**. At most 5,000 characters. |
| `deadline` | Optional. If omitted or `null`, the stored value is **cleared**. Past dates are allowed; `overdue` flags them. |

`status`, `completedAt`, `id`, `version`, and the timestamps are not part of the request. The server
ignores unknown JSON fields, so sending `"status"` in a PUT body has no effect.

**PUT compared with PATCH:**

| | PUT (full replacement) | PATCH (JSON Merge Patch, RFC 7396) |
|---|---|---|
| Idempotent | Yes, by HTTP definition. Retrying is always safe. | Merge Patch happens to be idempotent, but PATCH in general is not. |
| `null` compared with an absent field | No ambiguity: absent and `null` both mean "no value". | `null` means delete and absent means keep. The server must tell them apart, which needs wrapper types or raw JSON handling. |
| Validation | The same whole-object validation as create. | Rules like "title required" apply only when the field is present, so there are more code paths. |
| Payload size | The client sends every editable field (4 small fields here). | The client sends only the changed fields. |
| Lost updates | A stale client can overwrite fields it never looked at, unless it uses a precondition (see §4). | Less likely, because only the changed fields are written. |
| Client complexity | Low: the edit form already holds every field. | Needs a diff, or careful construction of the request. |

The resource is small, the edit form sends all of its fields, and §4 removes the lost-update risk.
With that in place PATCH adds complexity without real benefit, so PATCH is **deliberately not
supported** and returns `405 Method Not Allowed`, with an `Allow` header that lists the supported
methods. It can be added later without breaking clients if the resource grows.

In the domain, all edits go through **one business method**, `Task.updateDetails(...)`, instead of
setters. It checks the editable rule, validates **every** value, and only then assigns them, so a
rejected edit never leaves the task half-updated.

### 2. Change status only through explicit action endpoints

| Request | Effect |
|---|---|
| `POST /api/v1/tasks/{id}/complete` | `status = COMPLETED` and `completedAt = now` (from the server clock) |
| `POST /api/v1/tasks/{id}/reopen` | `status = OPEN` and `completedAt = null` |

Both return `200 OK` with the updated task and its current `ETag`.

We rejected **putting `status` in the PUT body**. That would make PUT a mix of data edits and state
transitions, `completedAt` would have to be derived inside a generic update, and it would conflict
with the rule that completed tasks are read-only (a request that completes the task and changes its
title would be ambiguous).

We rejected **`PUT/DELETE /{id}/completion`**. It is more "pure" REST, but it is less clear to
frontend developers and adds no real guarantee, because the domain already makes the actions
idempotent (see §3).

These actions map directly to `Task.complete(Instant)` and `Task.reopen()`, so the API speaks in
business terms rather than exposing raw field writes.

### 3. Idempotency

| Request | Safe | Idempotent | Notes |
|---|---|---|---|
| `GET /{id}` | ✔ | ✔ | |
| `PUT /{id}` | | ✔ | Sending the same body again changes nothing, so neither `version` nor `updatedAt` advances. |
| `POST /{id}/complete` | | ✔ (by design) | Completing a completed task keeps the original `completedAt` and version. |
| `POST /{id}/reopen` | | ✔ (by design) | Reopening an open task does nothing. |
| `DELETE /{id}` | | ✔ | The server state after one or many calls is the same. Only the status code differs: `204` the first time, then `404`. |

Because repeated no-op requests leave the entity unchanged, JPA issues no `UPDATE`, and the version and
ETag stay the same. The integration test checks this.

### 4. Optimistic concurrency with `ETag` and `If-Match`

- `tasks.version` (`BIGINT`, migration `V2`) is a JPA `@Version` counter.
- Every single-task response (`POST` create, `GET /{id}`, `PUT`, `complete`, `reopen`) includes a
  strong `ETag` derived from the representation. It is the version, plus an `-overdue` suffix while the
  task is overdue: `"5"` or `"5-overdue"`. The body also includes `version`, which matters for list
  views because they have no per-item headers.
- `PUT`, `complete`, `reopen`, and `DELETE` accept an **optional** `If-Match`:
  - `If-Match: "3"` (or `"3-overdue"`): the request goes ahead only if the task is at version 3.
    Otherwise the response is **`412 Precondition Failed`** and nothing changes.
  - `If-Match: *` or no header: the request is unconditional.
  - A weak tag (`W/"3"`), a list of tags, or a malformed value returns **`400 Bad Request`**, because the
    API only issues single strong tags.
- Checks run in this order: **existence (404) → precondition (412) → business rule (409)**. This
  follows RFC 9110, where a precondition is ignored when the request would fail anyway, and a
  precondition is evaluated before the method's own semantics.
- If a write races with another transaction *between* the read and the write, Hibernate's versioned
  `UPDATE`/`DELETE` affects zero rows. The resulting `OptimisticLockingFailureException` is returned as
  **`409 Conflict`**. It is not a `412`, because the client may not have sent a precondition at all.

`If-Match` is optional so that simple clients keep working. It is not mandatory with `428 Precondition
Required` for now; see the consequences below.

**Why add an `-overdue` suffix?** A strong ETag must change whenever the representation changes. The
derived `overdue` flag changes with the calendar date even though the stored task, and so its version,
stays the same. Spring automatically answers `GET` requests carrying `If-None-Match` with
`304 Not Modified`, and browsers send that header on their own. If the ETag were only the version, a
cached `overdue: false` would be served after the deadline passed. With the suffix, the ETag changes
when the flag flips, and conditional GETs stay correct.

`If-Match` compares **only the version**. The `overdue` flag is not stored state, so a concurrent
write cannot lose it, and rejecting an edit just because midnight passed would add friction without
protecting anything. This makes `If-Match` slightly more lenient than a byte-for-byte strong
comparison, and we accept that deliberately.

Application timestamps are truncated to microseconds, the precision of PostgreSQL `timestamptz`. As a
result, `completedAt` and `updatedAt` in a write response are identical to what later reads return.

### 5. Deletion: hard delete; a repeated or unknown delete returns `404`

- `DELETE /{id}` physically removes the row and returns `204 No Content`.
- An unknown ID, including one that was already deleted, returns `404` with a problem-details body.
- Completed tasks can be deleted. Deleting is not an edit, so it does not need a reopen first.

We rejected **soft delete (`deleted_at`)**. The product brief says "once deleted it's gone". Soft
delete would add a filter to every query, complicate uniqueness and indexes, and imply undelete and
retention features that are not in scope. If audit or undo becomes a requirement, a separate
`task_events` or archive table is a cleaner option.

We rejected **always returning `204`**, even when the task does not exist. It would hide bugs such as a
wrong ID or a stale client cache. `404` keeps delete consistent with the other endpoints, and the
server state is idempotent either way.

### 6. Error mapping (RFC 9457 `application/problem+json`)

| Status | When |
|---|---|
| `400` | Validation failure (listed per field under `errors`), malformed JSON, non-numeric ID, unsupported `If-Match` |
| `404` | The task does not exist |
| `405` | `PATCH` or another unsupported method |
| `409` | Editing a completed task (`TaskNotEditableException`), or a concurrent write detected at commit |
| `412` | `If-Match` does not match the current version (`TaskVersionMismatchException`) |

Domain exceptions extend new abstract bases in `common.exception` (`ConflictException` and
`PreconditionFailedException`, alongside the existing `NotFoundException`). As a result,
`GlobalExceptionHandler` maps whole categories of error, not individual task exceptions.

## Consequences

- ✅ Business rules live in the entity (`updateDetails`, `complete`, `reopen`), not in the API layer, and
  every edit is atomic.
- ✅ Clients that use `If-Match` are protected from lost updates. The API is idempotent and safe to retry.
- ✅ The API's behaviour is covered by unit, WebMvc, repository, and end-to-end Testcontainers tests.
- ⚠️ Clients that omit `If-Match` can still overwrite each other: the last writer wins. If this becomes a
  problem, `If-Match` can be made mandatory (`428 Precondition Required`) in a later API version.
- ⚠️ PUT clients must send every editable field. A missing `description` or `deadline` clears it.
- ⚠️ Hard delete cannot be undone.
- ⚠️ Role-based authorization for delete is deferred until authentication exists.
