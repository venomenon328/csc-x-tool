package de.venomenon.cscxtool.participant;

import java.time.LocalDate;

record BotbSelectionCommand(Long id, int editionNumber, String artist, LocalDate knownSince) {
}
