package de.venomenon.cscxtool.entry;

import tools.jackson.databind.JsonNode;

record UpdateContestEntryAssessmentRequest(Integer assessment, Integer assessmentConfidence) {

    static UpdateContestEntryAssessmentRequest from(JsonNode input) {
        if (input == null || !input.isObject() || !input.has("assessment") || !input.has("assessmentConfidence")) {
            return null;
        }
        JsonNode assessment = input.get("assessment");
        JsonNode confidence = input.get("assessmentConfidence");
        if (!isNullableInteger(assessment) || !isNullableInteger(confidence)) {
            return null;
        }
        return new UpdateContestEntryAssessmentRequest(nullableInteger(assessment), nullableInteger(confidence));
    }

    private static boolean isNullableInteger(JsonNode value) {
        return value != null && (value.isNull() || (value.isIntegralNumber() && value.canConvertToInt()));
    }

    private static Integer nullableInteger(JsonNode value) {
        return value.isNull() ? null : value.intValue();
    }
}
