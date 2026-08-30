package de.venomenon.cscxtool.contest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CreateContestParticipationRequest(
        @Positive(message = "Die Teilnehmer-ID muss positiv sein.") long participantId,
        @NotBlank(message = "Das Land darf nicht leer sein.") String countryCode,
        Boolean active
) {
}
