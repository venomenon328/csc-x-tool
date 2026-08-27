package de.venomenon.cscxtool.data;

public record RestorePreview(
        String token,
        String sourceType,
        String sourceName,
        String createdAt,
        String applicationVersion,
        int schemaVersion,
        boolean compatible,
        RestoreDataCounts counts
) {
}
