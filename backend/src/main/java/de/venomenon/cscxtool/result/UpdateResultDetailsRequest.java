package de.venomenon.cscxtool.result;

record UpdateResultDetailsRequest(
        Integer officialTotalPoints,
        Integer finalPlace,
        Boolean finalPlaceTied
) {
}
