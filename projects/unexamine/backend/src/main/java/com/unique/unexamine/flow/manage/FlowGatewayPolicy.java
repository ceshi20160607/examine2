package com.unique.unexamine.flow.manage;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 网关在物理节点上继续使用 GATEWAY，通过结构化模式区分选一路、并行、包容和汇合。
 * 缺少模式的旧版本按排他网关读取，保证已发布实例不因新能力改变行为。
 */
final class FlowGatewayPolicy {
    static final String CONFIG_KEY = "gatewayMode";
    static final String MERGE_NODE_KEY = "mergeNodeKey";
    static final String SPLIT_NODE_KEY = "splitNodeKey";
    static final String EXCLUSIVE = "EXCLUSIVE";
    static final String PARALLEL = "PARALLEL";
    static final String INCLUSIVE = "INCLUSIVE";
    static final String MERGE = "MERGE";
    static final Set<String> MODES = Set.of(EXCLUSIVE, PARALLEL, INCLUSIVE, MERGE);

    private FlowGatewayPolicy() {
    }

    static String mode(Map<String, Object> config) {
        Object configured = config == null ? null : config.get(CONFIG_KEY);
        if (configured == null || String.valueOf(configured).isBlank()) return EXCLUSIVE;
        return String.valueOf(configured).strip().toUpperCase(Locale.ROOT);
    }

    static boolean split(String mode) {
        return PARALLEL.equals(mode) || INCLUSIVE.equals(mode);
    }
}
