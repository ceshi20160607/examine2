package com.unique.unexamine.authentication.manage;

import java.time.LocalDateTime;
import java.util.List;

/** 身份源测试结果；testedTime 为本次测试完成时间。 */
public record SsoProviderTestReport(
        String status,
        String requestId,
        String failureCode,
        LocalDateTime testedTime,
        List<SsoProviderTestCheck> checks) {
}
