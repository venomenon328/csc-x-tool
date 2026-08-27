package de.venomenon.cscxtool.candidate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

record ReorderCandidatesRequest(
        @NotNull(message = "Die vollständige Kandidatenreihenfolge muss angegeben werden.")
        List<@NotNull @Positive Long> candidateIds
) {
}
