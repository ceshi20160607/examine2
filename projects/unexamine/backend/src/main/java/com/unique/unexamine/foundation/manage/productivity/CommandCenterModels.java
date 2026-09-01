package com.unique.unexamine.foundation.manage.productivity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class CommandCenterModels {
    private CommandCenterModels() {
    }

    public record Item(
            String id,
            String groupCode,
            String groupName,
            String label,
            String description,
            String target,
            String moduleCode,
            Long recordId) {
    }

    public record State(List<String> favoriteIds, List<String> recentIds, Integer version) {
    }

    public record InvalidatedItem(String id, String reason) {
    }

    public record View(
            List<Item> results,
            List<Item> favorites,
            List<Item> recent,
            State state,
            List<InvalidatedItem> invalidated) {
    }

    public record UpdateStateRequest(
            @NotNull @Size(max = 20) List<@NotBlank @Size(max = 120) String> favoriteIds,
            @NotNull @Size(max = 10) List<@NotBlank @Size(max = 120) String> recentIds,
            Integer expectedVersion) {
    }

    public record ExecuteRequest(@NotBlank @Size(max = 120) String commandId) {
    }

    public record Execution(Item command, State state, List<InvalidatedItem> invalidated) {
    }
}
