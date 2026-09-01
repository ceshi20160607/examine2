package com.unique.unexamine.authentication.manage;

import java.util.List;

public record SsoProviderAdminView(
        Long id,
        String code,
        String name,
        String status,
        Long publishedVersionId,
        Integer publishedVersionNumber,
        List<SsoProviderVersionView> versions) {
}
