package de.venomenon.cscxtool.entry;

import java.util.Optional;

/** One structural source format for historical entry imports. */
interface HistoricalEntryImportFormatStrategy {

    Optional<HistoricalEntryImportParseResult> parse(HistoricalImportSourceLine source);
}
