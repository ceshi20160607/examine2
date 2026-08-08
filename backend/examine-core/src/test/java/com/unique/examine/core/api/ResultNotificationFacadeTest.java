package com.unique.examine.core.api;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResultNotificationFacadeTest {
    @Test
    void commandFreezesBoundedVariablesTargetAndSystemLocalPath() {
        var command = new ResultNotificationFacade.Command(10, 20, 30, 30,
                "MODULE_EXPORT_SUCCEEDED", Map.of("moduleCode", "order", "rows", "2"),
                new AggregateRef("MODULE_EXPORT_TASK", "40"),
                "/systems/10/workbench?module=order&panel=export&task=40",
                "job-result:export:40:SUCCEEDED");

        assertThat(command.variables()).containsEntry("rows", "2").isUnmodifiable();
        assertThat(command.target().type()).isEqualTo("MODULE_EXPORT_TASK");
        assertThatThrownBy(() -> new ResultNotificationFacade.Command(10, 20, 30, 30,
                command.templateCode(), Map.of("invalid-key", "value"), command.target(),
                command.targetPath(), command.dedupeKey())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResultNotificationFacade.Command(10, 20, 30, 30,
                command.templateCode(), Map.of(), command.target(),
                "/systems/11/workbench", command.dedupeKey())).isInstanceOf(IllegalArgumentException.class);
    }
}
