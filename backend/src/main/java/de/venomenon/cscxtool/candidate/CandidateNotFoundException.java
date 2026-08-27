package de.venomenon.cscxtool.candidate;

public class CandidateNotFoundException extends RuntimeException {

    public CandidateNotFoundException(long candidateId, long showId) {
        super("Candidate " + candidateId + " was not found in show " + showId + ".");
    }
}
