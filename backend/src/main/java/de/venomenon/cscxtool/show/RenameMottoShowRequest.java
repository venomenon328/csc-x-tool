package de.venomenon.cscxtool.show;

import jakarta.validation.constraints.NotBlank;

record RenameMottoShowRequest(@NotBlank(message = "Der Show-Name darf nicht leer sein.") String name) {
}
