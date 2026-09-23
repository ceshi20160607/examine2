package com.unique.unexamine.flow.manage;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 覆盖子流程变量继承、显式映射和缺失变量边界，避免运行时用松散 JSON 猜测配置。 */
class FlowSubflowPolicyTest {
    @Test
    void mapsInputsAndOutputsWithExplicitNames() {
        var policy = FlowSubflowPolicy.parse(Map.of(
                "flowId", 12,
                "inheritAllVariables", false,
                "inputMappings", List.of(Map.of("sourceKey", "customerId", "targetKey", "subjectId")),
                "outputMappings", List.of(Map.of("sourceKey", "approvedCode", "targetKey", "childResult"))));

        assertEquals(Map.of("subjectId", 88),
                FlowSubflowPolicy.childVariables(policy, Map.of("customerId", 88, "privateValue", "hidden")));
        assertEquals(Map.of("childResult", "OK"),
                FlowSubflowPolicy.parentOutputs(policy, Map.of("approvedCode", "OK")));
    }

    @Test
    void inheritsVariablesBeforeApplyingRename() {
        var policy = FlowSubflowPolicy.parse(Map.of(
                "flowId", 12,
                "inputMappings", List.of(Map.of("sourceKey", "name", "targetKey", "displayName"))));
        assertEquals(Map.of("name", "张三", "displayName", "张三"),
                FlowSubflowPolicy.childVariables(policy, Map.of("name", "张三")));
    }

    @Test
    void rejectsDuplicateTargetsAndMissingRuntimeVariables() {
        assertThrows(IllegalArgumentException.class, () -> FlowSubflowPolicy.parse(Map.of(
                "flowId", 12,
                "inputMappings", List.of(
                        Map.of("sourceKey", "a", "targetKey", "same"),
                        Map.of("sourceKey", "b", "targetKey", "same")))));

        var policy = FlowSubflowPolicy.parse(Map.of(
                "flowId", 12,
                "inheritAllVariables", false,
                "inputMappings", List.of(Map.of("sourceKey", "required", "targetKey", "required"))));
        assertThrows(IllegalArgumentException.class,
                () -> FlowSubflowPolicy.childVariables(policy, Map.of("other", 1)));
    }
}
