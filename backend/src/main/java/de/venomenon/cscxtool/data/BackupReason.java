package de.venomenon.cscxtool.data;

public enum BackupReason {
    STARTUP, PRE_MIGRATION, MANUAL, PRE_RESTORE;

    public boolean automatic() {
        return this == STARTUP || this == PRE_MIGRATION;
    }
}
