package de.venomenon.cscxtool.entry;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

record UpdateContestEntryRequest(
        @NotBlank(message = "Der Interpret darf nicht leer sein.") String artist,
        @NotBlank(message = "Der Titel darf nicht leer sein.") String title,
        @NotBlank(message = "Der YouTube-Link darf nicht leer sein.") String youtubeUrl,
        String comment,
        @NotNull(message = "Der H\u00f6rstatus muss angegeben werden.") Boolean listened,
        @NotNull(message = "Die Wiedervorlage muss angegeben werden.") Boolean relisten
) {
}
