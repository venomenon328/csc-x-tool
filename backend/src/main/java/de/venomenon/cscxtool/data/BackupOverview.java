package de.venomenon.cscxtool.data;

import java.nio.file.Path;
import java.util.List;

public record BackupOverview(
        String databaseLocation,
        String automaticBackupsLocation,
        String manualBackupsLocation,
        String exportsLocation,
        BackupSummary lastBackup,
        List<BackupSummary> automaticBackups,
        List<BackupSummary> manualBackups
) {
    static BackupOverview of(
            Path database, Path automatic, Path manual, Path exports,
            BackupSummary latest, List<BackupSummary> automaticBackups, List<BackupSummary> manualBackups
    ) {
        return new BackupOverview(
                database.toString(), automatic.toString(), manual.toString(), exports.toString(), latest,
                List.copyOf(automaticBackups), List.copyOf(manualBackups)
        );
    }
}
