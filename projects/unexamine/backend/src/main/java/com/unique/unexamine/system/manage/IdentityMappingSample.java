package com.unique.unexamine.system.manage;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IdentityMappingSample(
        @NotBlank @Size(max = 255) String externalUserId,
        @NotBlank @Size(max = 255) String externalDepartment,
        @Email @Size(max = 255) String email,
        @Size(max = 40) String mobile,
        @Size(max = 100) String employeeNo,
        @NotBlank @Size(max = 100) String displayName,
        @Size(max = 32) String mfaLevel,
        @Size(max = 255) String device,
        @Size(max = 64) String requestId) {
}
