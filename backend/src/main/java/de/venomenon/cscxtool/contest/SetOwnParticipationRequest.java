package de.venomenon.cscxtool.contest;

/** A null participation deliberately clears the explicitly chosen own contest identity. */
public record SetOwnParticipationRequest(Long participationId, Boolean confirmChange) {
    boolean isConfirmed() {
        return Boolean.TRUE.equals(confirmChange);
    }
}
