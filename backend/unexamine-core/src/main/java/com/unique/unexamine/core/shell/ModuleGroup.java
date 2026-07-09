package com.unique.unexamine.core.shell;

import java.util.List;

public record ModuleGroup(
        String code,
        String label,
        List<BusinessModule> modules
) {
}
