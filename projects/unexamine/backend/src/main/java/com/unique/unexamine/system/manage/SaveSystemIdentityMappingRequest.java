package com.unique.unexamine.system.manage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record SaveSystemIdentityMappingRequest(
        @NotNull Long providerId,
        @NotNull Long providerVersionId,
        @NotBlank @Size(max = 255) String tenantDomain,
        @NotEmpty @Size(max = 200) Map<@NotBlank @Size(max = 255) String, @NotNull Long> departmentMappings,
        @NotEmpty @Size(max = 4) List<@Pattern(regexp = "EXTERNAL_USER_ID|EMAIL|MOBILE|EMPLOYEE_NO") String> matchOrder,
        @NotBlank @Pattern(regexp = "ACCESS_REQUEST|CREATE_MEMBER|CREATE_ACCOUNT_AND_MEMBER") String jitPolicy,
        Integer expectedVersion) {
}
