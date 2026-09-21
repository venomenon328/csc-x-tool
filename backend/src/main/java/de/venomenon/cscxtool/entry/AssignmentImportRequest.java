package de.venomenon.cscxtool.entry;

import java.util.List;

record AssignmentImportRequest(List<AssignmentImportItem> assignments) { }

record AssignmentImportItem(
        Long entryId, Long participationId, Long expectedParticipationId, boolean confirmReplacement
) { }
