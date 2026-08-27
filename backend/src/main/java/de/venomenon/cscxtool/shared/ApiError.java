package de.venomenon.cscxtool.shared;

import java.time.Instant;

record ApiError(Instant timestamp, int status, String error, String message, String path) {
}
