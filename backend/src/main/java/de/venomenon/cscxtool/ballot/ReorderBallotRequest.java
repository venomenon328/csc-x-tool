package de.venomenon.cscxtool.ballot;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

record ReorderBallotRequest(
        @NotNull(message = "Die vollst\u00e4ndige Rangliste muss angegeben werden.")
        List<@NotNull @Positive Long> rankedEntryIds,
        @NotNull(message = "Der vollst\u00e4ndige ungeordnete Pool muss angegeben werden.")
        List<@NotNull @Positive Long> unrankedEntryIds
) {
}
