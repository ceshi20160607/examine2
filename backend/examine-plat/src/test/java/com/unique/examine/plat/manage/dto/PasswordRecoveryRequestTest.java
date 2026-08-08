package com.unique.examine.plat.manage.dto;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordRecoveryRequestTest {

    @Test
    void freezesBoundedRequestAndResetShapes() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(new PasswordRecoveryRequest("owner@example.com"))).isEmpty();
            assertThat(validator.validate(new PasswordRecoveryRequest(""))).isNotEmpty();
            assertThat(validator.validate(new PasswordRecoveryRequest("x".repeat(255)))).isNotEmpty();

            assertThat(validator.validate(new PasswordRecoveryResetRequest(
                    "A".repeat(43), "replacement-password"))).isEmpty();
            assertThat(validator.validate(new PasswordRecoveryResetRequest(
                    "not a token", "replacement-password"))).isNotEmpty();
            assertThat(validator.validate(new PasswordRecoveryResetRequest(
                    "A".repeat(43), "short"))).isNotEmpty();
        }
        assertThat(PasswordRecoveryRequest.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("account");
        assertThat(PasswordRecoveryResetRequest.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("token", "newPassword");
    }
}
