package de.venomenon.cscxtool.entry;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

record ReorderContestEntriesRequest(
        @NotNull(message = "Die vollständige manuelle Reihenfolge muss angegeben werden.")
        List<@NotNull @Positive Long> entryIds
) {
}
