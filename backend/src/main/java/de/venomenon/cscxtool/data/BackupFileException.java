package de.venomenon.cscxtool.data;

/** A known, user-actionable issue with the submitted backup/export artifact. */
public class BackupFileException extends RuntimeException {
    private final String code;

    public BackupFileException(String code, String message) {
        super(message);
        this.code = code;
    }

    public BackupFileException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() { return code; }
}
