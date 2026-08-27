package de.venomenon.cscxtool.data;

import jakarta.validation.constraints.NotBlank;

public record ConfirmRestoreRequest(@NotBlank(message = "Die Wiederherstellung muss ausdrücklich bestätigt werden.") String token) { }
