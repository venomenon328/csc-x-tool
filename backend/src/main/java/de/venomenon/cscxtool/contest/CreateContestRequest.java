package de.venomenon.cscxtool.contest;

import jakarta.validation.constraints.NotBlank;

record CreateContestRequest(@NotBlank(message = "Der Contestname darf nicht leer sein.") String name) {
}
