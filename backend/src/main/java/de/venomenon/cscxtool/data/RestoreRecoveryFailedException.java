package de.venomenon.cscxtool.data;

/**
 * The live restore failed and the immediately following safety-backup recovery could not be
 * verified either. The live data state must not be described as known in this situation.
 */
public final class RestoreRecoveryFailedException extends BackupStorageException {

    public RestoreRecoveryFailedException(Throwable restoreFailure, Throwable recoveryFailure) {
        super("Die Wiederherstellung und die automatische R\u00fccksicherung sind technisch fehlgeschlagen.", restoreFailure);
        addSuppressed(recoveryFailure);
    }
}
