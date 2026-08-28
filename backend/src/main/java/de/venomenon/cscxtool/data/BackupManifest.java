package de.venomenon.cscxtool.data;

import java.time.Instant;

record BackupManifest(
        int backupFormatVersion,
        Instant createdAt,
        String applicationVersion,
        int schemaVersion,
        BackupReason reason,
        String databaseSha256
) {
    static final int FORMAT_VERSION = 1;
}
