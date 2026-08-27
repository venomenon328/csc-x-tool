package de.venomenon.cscxtool.shared;

import java.time.Instant;

record ApiError(Instant timestamp, int status, String code, String message, String path) {
}
