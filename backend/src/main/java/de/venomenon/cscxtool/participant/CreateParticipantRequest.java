package de.venomenon.cscxtool.participant;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

record CreateParticipantRequest(
        @NotBlank(message = "Der Anzeigename darf nicht leer sein.") String displayName,
        Boolean active,
        List<String> aliases
) {
}
