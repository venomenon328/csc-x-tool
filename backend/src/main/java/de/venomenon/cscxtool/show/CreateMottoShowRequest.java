package de.venomenon.cscxtool.show;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

record CreateMottoShowRequest(
        @Positive(message = "Die Shownummer muss positiv sein.") int showNumber,
        @NotBlank(message = "Der Show-Name darf nicht leer sein.") String name
) {
}
