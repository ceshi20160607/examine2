package com.unique.examine.core.security;

import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public final class PlatPermissionSupport {

    private PlatPermissionSupport() {}

    public static Set<String> parseCodes(String platPermCodes) {
        if (!StringUtils.hasText(platPermCodes)) {
            return Collections.emptySet();
        }
        return Arrays.stream(platPermCodes.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }

    public static boolean hasCode(String platPermCodes, String code) {
        if (!StringUtils.hasText(code)) {
            return false;
        }
        return parseCodes(platPermCodes).contains(code.trim());
    }
}
