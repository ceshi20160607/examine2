package com.unique.unexamine.moduleconfig.manage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateModuleRequest(
        Long groupId,
        @Size(max = 200) String groupName,
        @Size(max = 50) @Pattern(regexp = "[A-Za-z][A-Za-z0-9_-]{1,49}") String code,
        @NotBlank @Size(max = 200) String name) {
}
