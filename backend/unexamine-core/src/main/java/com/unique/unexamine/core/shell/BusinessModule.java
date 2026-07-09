package com.unique.unexamine.core.shell;

import java.util.List;

public record BusinessModule(
        String code,
        String label,
        String purpose,
        List<String> ownedConfig
) {
}
