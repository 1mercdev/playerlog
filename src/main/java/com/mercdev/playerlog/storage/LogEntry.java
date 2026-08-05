package com.mercdev.playerlog.storage;

import java.util.UUID;

/**
 * A single log entry. Once written it is never modified - only appended
 * or wiped wholesale via clearlog, similar in spirit to a git commit.
 */
public record LogEntry(
        long id,
        UUID playerUuid,
        String playerName,
        UUID authorUuid,
        String authorName,
        long timestampMillis,
        String message
) {
}
