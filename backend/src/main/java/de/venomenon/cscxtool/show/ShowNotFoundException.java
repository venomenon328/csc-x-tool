package de.venomenon.cscxtool.show;

public class ShowNotFoundException extends RuntimeException {

    public ShowNotFoundException(long showId) {
        super("Die Mottoshow mit der ID " + showId + " wurde nicht gefunden.");
    }
}
