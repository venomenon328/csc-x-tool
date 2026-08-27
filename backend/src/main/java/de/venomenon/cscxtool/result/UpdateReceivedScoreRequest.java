package de.venomenon.cscxtool.result;

import jakarta.validation.constraints.NotNull;

record UpdateReceivedScoreRequest(
        @NotNull(message = "Der Ergebnisstatus muss angegeben werden.") ReceivedScoreStatus status,
        Integer points
) {
}
