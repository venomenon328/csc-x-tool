package de.venomenon.cscxtool.show;

import java.time.Instant;

record MottoShow(long id, int showNumber, String name, Instant createdAt, Instant updatedAt) {
}
