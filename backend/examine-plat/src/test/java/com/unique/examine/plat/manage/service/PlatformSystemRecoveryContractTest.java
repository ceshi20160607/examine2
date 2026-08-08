package com.unique.examine.plat.manage.service;

import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformSystemRecoveryContractTest {
    @Test
    void exposesOnlyStableBoundedFailureCodes() {
        assertThat(PlatformSystemAdminService.initializationFailureCode(
                new BusinessException("provider.secret/leak", "secret host", org.springframework.http.HttpStatus.BAD_REQUEST)))
                .isEqualTo("PROVIDER_SECRET_LEAK");
        assertThat(PlatformSystemAdminService.initializationFailureCode(
                new DataIntegrityViolationException("jdbc:mysql://secret-host")))
                .isEqualTo("INITIALIZATION_DATA_CONFLICT");
        assertThat(PlatformSystemAdminService.initializationFailureCode(
                new IllegalStateException("vault://MUST_NOT_LEAK")))
                .isEqualTo("INITIALIZATION_ILLEGAL_STATE_EXCEPTION");
    }

    @Test
    void failedInitializationCannotBypassExplicitRetryThroughActivation() {
        assertThatThrownBy(() -> PlatformSystemAdminService.nextStatus("INIT_FAILED", "activate"))
                .isInstanceOf(BusinessException.class);
    }
}
