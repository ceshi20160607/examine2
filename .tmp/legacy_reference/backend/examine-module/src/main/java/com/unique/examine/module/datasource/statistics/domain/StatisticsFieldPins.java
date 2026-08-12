package com.unique.examine.module.datasource.statistics.domain;

import java.util.regex.Pattern;

/** Expected immutable field identities supplied by a published consumer. */
public record StatisticsFieldPins(
        Field measure,
        Field group,
        Field time
) {
    public record Field(
            long logicalFieldId,
            String code,
            String name,
            String type,
            String queryType
    ) {
        private static final Pattern CODE =
                Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,63}$");

        public Field {
            if (logicalFieldId <= 0 || code == null
                    || !CODE.matcher(code).matches()
                    || name == null || name.isBlank() || name.length() > 200
                    || type == null || type.isBlank() || type.length() > 100
                    || queryType == null || queryType.isBlank()
                    || queryType.length() > 100) {
                throw new StatisticsException(
                        "STATISTICS_SOURCE_UNAVAILABLE",
                        "Pinned statistics field metadata is invalid");
            }
            name = name.strip();
            type = type.strip();
            queryType = queryType.strip();
        }
    }
}
