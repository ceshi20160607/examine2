package com.unique.unexamine.core.shell;

import java.util.List;

public record ShellActionItem(
        String code,
        String title,
        String description,
        String category,
        String status,
        String owner,
        String dueAt,
        List<String> tags
) {
}
