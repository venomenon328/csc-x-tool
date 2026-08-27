package de.venomenon.cscxtool.participant;

public class ParticipantNotFoundException extends RuntimeException {

    public ParticipantNotFoundException(long participantId) {
        super("Participant " + participantId + " was not found.");
    }
}
