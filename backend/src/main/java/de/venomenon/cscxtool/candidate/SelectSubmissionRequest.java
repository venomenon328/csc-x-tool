package de.venomenon.cscxtool.candidate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

record SelectSubmissionRequest(
        @NotNull(message = "Der einzureichende Kandidat muss angegeben werden.") @Positive long candidateId,
        boolean confirmReplacement
) {
}
