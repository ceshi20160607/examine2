package com.unique.examine.plat.manage.dto;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChangePasswordRequestTest {

    @Test
    void acceptsOnlyTheFrozenBoundedShape() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();

            assertThat(validator.validate(new ChangePasswordRequest(
                    "current", "replacement-password"))).isEmpty();
            assertThat(validator.validate(new ChangePasswordRequest(
                    "", "replacement-password"))).isNotEmpty();
            assertThat(validator.validate(new ChangePasswordRequest(
                    "x".repeat(201), "replacement-password"))).isNotEmpty();
            assertThat(validator.validate(new ChangePasswordRequest(
                    "current", "short"))).isNotEmpty();
            assertThat(validator.validate(new ChangePasswordRequest(
                    "current", "x".repeat(201)))).isNotEmpty();
        }

        assertThat(ChangePasswordRequest.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("currentPassword", "newPassword");
    }
}
