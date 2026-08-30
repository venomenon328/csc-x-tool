package de.venomenon.cscxtool.contest;

import jakarta.validation.constraints.NotBlank;

public record UpdateContestParticipationRequest(
        @NotBlank(message = "Das Land darf nicht leer sein.") String countryCode,
        Boolean active
) {
}
