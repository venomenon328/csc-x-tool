package de.venomenon.cscxtool.candidate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

record UpdateCandidateRequest(
        @NotBlank(message = "Der Interpret darf nicht leer sein.") String artist,
        @NotBlank(message = "Der Titel darf nicht leer sein.") String title,
        @NotBlank(message = "Der YouTube-Link darf nicht leer sein.") String youtubeUrl,
        String comment,
        @NotNull(message = "Der Status muss angegeben werden.") CandidateStatus status
) {
}
