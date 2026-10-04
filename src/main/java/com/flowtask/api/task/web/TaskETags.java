package com.flowtask.api.task.web;

import org.springframework.http.ETag;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps a task representation to and from HTTP entity tags.
 * <p>
 * The tag is the task's optimistic-locking version, plus an {@code -overdue} suffix while the task is
 * overdue (e.g. {@code "5"} or {@code "5-overdue"}). The suffix makes the tag change when the derived
 * {@code overdue} flag flips with the calendar, so conditional GETs ({@code If-None-Match}) never serve a
 * stale flag. {@code If-Match} compares only the version, because {@code overdue} is not stored state and
 * cannot be lost by a concurrent write.
 */
final class TaskETags {

    static final String INVALID_IF_MATCH_DETAIL =
            "If-Match must be * or a single strong ETag previously returned by this API, such as \"3\"";

    private static final String OVERDUE_SUFFIX = "-overdue";
    private static final Pattern TAG = Pattern.compile("(\\d{1,18})(" + OVERDUE_SUFFIX + ")?");

    private TaskETags() {
    }

    static String of(TaskResponse task) {
        return "\"" + task.version() + (task.overdue() ? OVERDUE_SUFFIX : "") + "\"";
    }

    /**
     * Returns the version the client expects, or {@code null} when the header is absent, blank or
     * {@code *} (no version precondition).
     *
     * @throws ResponseStatusException {@code 400} for weak, multiple or malformed entity tags
     */
    static Long expectedVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            return null;
        }
        List<ETag> tags = ETag.parse(ifMatch);
        if (tags.size() == 1) {
            ETag tag = tags.getFirst();
            if (tag.isWildcard()) {
                return null;
            }
            Matcher matcher = TAG.matcher(tag.tag());
            if (!tag.weak() && matcher.matches()) {
                return Long.parseLong(matcher.group(1));
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_IF_MATCH_DETAIL);
    }
}
