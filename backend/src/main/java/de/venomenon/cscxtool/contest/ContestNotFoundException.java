package de.venomenon.cscxtool.contest;

public class ContestNotFoundException extends RuntimeException {
    public ContestNotFoundException(long contestId) {
        super("Die CSC-Ausgabe mit der ID " + contestId + " wurde nicht gefunden.");
    }
}
