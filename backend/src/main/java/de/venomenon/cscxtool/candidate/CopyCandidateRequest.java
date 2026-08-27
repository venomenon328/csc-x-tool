package de.venomenon.cscxtool.candidate;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

record CopyCandidateRequest(
        @NotEmpty(message = "Mindestens eine Zielshow muss ausgewählt werden.")
        List<@NotNull @Positive Long> targetShowIds
) {
}
