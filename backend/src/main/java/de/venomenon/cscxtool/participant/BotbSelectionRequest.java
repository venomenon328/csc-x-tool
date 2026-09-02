package de.venomenon.cscxtool.participant;

import java.time.LocalDate;

record BotbSelectionRequest(Long id, Integer editionNumber, String artist, LocalDate knownSince) {
}
