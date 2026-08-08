package com.unique.examine.flow.domain;

public record RecordStatusMapping(
        String fieldCode,
        String approvedValue,
        String rejectedValue,
        String withdrawnValue,
        String terminatedValue
) {
    private static final String FIELD_CODE_PATTERN = "^[A-Za-z][A-Za-z0-9_]{0,63}$";
    private static final String OPTION_ID_PATTERN = "^[1-9][0-9]{0,18}$";

    public RecordStatusMapping {
        if (fieldCode == null || !fieldCode.matches(FIELD_CODE_PATTERN)) {
            throw new IllegalArgumentException("Record status mapping field code is invalid");
        }
        requireOptionId(approvedValue);
        requireOptionId(rejectedValue);
        requireOptionId(withdrawnValue);
        requireOptionId(terminatedValue);
    }

    private static void requireOptionId(String value) {
        if (value == null || !value.matches(OPTION_ID_PATTERN)) {
            throw new IllegalArgumentException(
                    "Record status mapping values must be canonical positive integer strings"
            );
        }
    }
}
