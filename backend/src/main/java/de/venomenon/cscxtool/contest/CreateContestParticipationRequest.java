package de.venomenon.cscxtool.contest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.AssertTrue;
import java.util.List;

public record CreateContestParticipationRequest(
        @Positive(message = "Die Teilnehmer-ID muss positiv sein.") Long participantId,
        String displayName,
        List<String> aliases,
        @NotBlank(message = "Das Land darf nicht leer sein.") String countryCode,
        Boolean active
) {

    /** A request either assigns an existing identity or creates one together with its first participation. */
    @AssertTrue(message = "Eine bestehende Teilnehmer-ID oder ein Anzeigename für eine neue Identität ist erforderlich.")
    public boolean hasExactlyOneIdentitySource() {
        return (participantId != null) != (displayName != null);
    }
}
