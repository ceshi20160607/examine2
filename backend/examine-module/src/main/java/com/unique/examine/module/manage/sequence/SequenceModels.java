package com.unique.examine.module.manage.sequence;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 自动编号接口模型。
 */
public final class SequenceModels {

    private SequenceModels() {
    }

    /**
     * 序号分配请求。
     *
     * @param tenantId 租户编号
     * @param sequenceType 序号类型
     * @param prefix 编号前缀
     * @param width 数字位宽
     * @param count 分配数量
     */
    public record SequenceAllocateRequest(String tenantId, String sequenceType, String prefix,
                                          Integer width, Integer count) {
    }

    /**
     * 序号分配结果。
     *
     * @param systemId 系统编号
     * @param tenantId 租户编号
     * @param moduleId 模块编号
     * @param sequenceType 序号类型
     * @param firstValue 起始数值
     * @param lastValue 结束数值
     * @param currentValue 分配后的当前数值
     * @param allocatedNos 已分配编号
     * @param atomic 是否原子分配
     * @param allocationStrategy 分配策略说明
     * @param traceId 链路追踪编号
     * @param auditLogId 审计日志编号
     * @param allocatedAt 分配时间
     */
    public record SequenceAllocationResult(String systemId, String tenantId, String moduleId, String sequenceType,
                                           long firstValue, long lastValue, long currentValue,
                                           List<String> allocatedNos, boolean atomic, String allocationStrategy,
                                           String traceId, String auditLogId, LocalDateTime allocatedAt) {
    }
}
