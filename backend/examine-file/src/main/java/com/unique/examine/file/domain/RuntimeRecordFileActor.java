package com.unique.examine.file.domain;

import com.unique.examine.core.runtime.RuntimeRecordAccessFacade;
import com.unique.examine.file.service.FileAssetService;

import java.util.LinkedHashSet;
import java.util.Set;

public record RuntimeRecordFileActor(
        long systemId,
        long tenantId,
        long memberId,
        Set<String> permissions,
        String moduleCode,
        long recordId
) {
    public RuntimeRecordFileActor {
        if (systemId <= 0 || tenantId <= 0 || memberId <= 0 || recordId <= 0) {
            throw new IllegalArgumentException("Runtime record file actor scope is incomplete");
        }
        if (moduleCode == null || !moduleCode.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) {
            throw new IllegalArgumentException("Runtime record file module code is invalid");
        }
        if (permissions == null
                || permissions.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("Runtime record file permissions are invalid");
        }
        permissions = Set.copyOf(permissions);
    }

    public RuntimeRecordAccessFacade.RuntimeRecordAccessRequest accessRequest() {
        return new RuntimeRecordAccessFacade.RuntimeRecordAccessRequest(
                systemId,
                tenantId,
                memberId,
                permissions,
                moduleCode,
                recordId);
    }

    public FileActor fileActor() {
        var mapped = new LinkedHashSet<String>();
        mapPermission(mapped, "file.create", FileAssetService.CREATE);
        mapPermission(mapped, "file.read", FileAssetService.READ);
        mapPermission(mapped, "file.reference", FileAssetService.REFERENCE);
        mapPermission(mapped, "file.manage", FileAssetService.MANAGE);
        return new FileActor(systemId, tenantId, memberId, Set.copyOf(mapped));
    }

    private void mapPermission(Set<String> mapped, String apiPermission, String domainPermission) {
        if (permissions.contains(apiPermission) || permissions.contains(domainPermission)) {
            mapped.add(domainPermission);
        }
    }
}
