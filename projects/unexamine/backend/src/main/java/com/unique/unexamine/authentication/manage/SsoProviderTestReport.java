package com.unique.unexamine.authentication.manage;

import java.time.LocalDateTime;
import java.util.List;

public record SsoProviderTestReport(
        String status,
        String requestId,
        String failureCode,
        LocalDateTime testedAt,
        List<SsoProviderTestCheck> checks) {
}
