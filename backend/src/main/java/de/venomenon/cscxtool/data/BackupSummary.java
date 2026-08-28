package de.venomenon.cscxtool.data;

import java.time.Instant;

public record BackupSummary(
        String id,
        Instant createdAt,
        String applicationVersion,
        int schemaVersion,
        BackupReason reason,
        long sizeBytes
) {
}
