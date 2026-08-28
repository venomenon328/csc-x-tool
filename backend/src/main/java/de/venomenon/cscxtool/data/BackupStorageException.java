package de.venomenon.cscxtool.data;

/** A technical storage failure. It deliberately is not a compatibility error. */
public class BackupStorageException extends RuntimeException {
    public BackupStorageException(String message, Throwable cause) { super(message, cause); }
}
