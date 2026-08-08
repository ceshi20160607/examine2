package com.unique.examine.core.api;

import java.util.List;

/** Platform-owned member and department choices exposed without leaking platform persistence. */
public interface RuntimeReferenceFacade {
    ReferenceCatalog resolve(long systemId, long tenantId, long memberId);

    record ReferenceOption(String value, String label) {
        public ReferenceOption {
            if (value == null || value.isBlank() || label == null || label.isBlank()) {
                throw new IllegalArgumentException("Runtime reference option is incomplete");
            }
        }
    }

    record ReferenceCatalog(
            Long primaryDepartmentId,
            List<ReferenceOption> members,
            List<ReferenceOption> departments
    ) {
        public ReferenceCatalog {
            if (primaryDepartmentId != null && primaryDepartmentId <= 0) {
                throw new IllegalArgumentException("Primary department id must be positive");
            }
            members = List.copyOf(members);
            departments = List.copyOf(departments);
        }
    }
}
