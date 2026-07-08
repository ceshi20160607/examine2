package com.unique.examine.web.command;

import java.util.List;

/**
 * Models for the role-aware command center.
 */
public final class CommandCenterModels {

    private CommandCenterModels() {
    }

    public record CommandCenterItem(
            String commandId,
            String label,
            String groupName,
            String description,
            String route,
            String commandType,
            boolean disabled,
            String disabledReason,
            String traceId
    ) {
    }

    public record CommandCenterResponse(
            String keyword,
            String systemId,
            String traceId,
            List<CommandCenterItem> items
    ) {
    }
}
