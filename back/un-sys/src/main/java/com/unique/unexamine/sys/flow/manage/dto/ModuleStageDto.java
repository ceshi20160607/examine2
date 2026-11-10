package com.unique.unexamine.sys.flow.manage.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 模块阶段的配置与运行入口模型；阶段线路仍由 Flow 快照承载。 */
public final class ModuleStageDto {
    private ModuleStageDto() {}

    /** 一次保存完整阶段、线路和触发规则，成功后新记录使用新 Flow 版本。 */
    public record SaveRequest(
            @Schema(description="阶段流程名称") String name,
            @Schema(description="阶段流程说明") String description,
            @Schema(description="启动阶段的记录或模块动作事件编码，如 CREATE") String triggerEventCode,
            @Schema(description="动作触发时的菜单主键；普通新建事件可为空") Long actionMenuId,
            @Schema(description="启动阶段流程的业务字段条件") JsonNode triggerCondition,
            @Schema(description="按顺序排列的阶段，第一项为起始阶段") List<Stage> stages,
            @Schema(description="阶段之间或到结束节点的线路") List<Route> routes) {}

    /** 阶段可关联的补充模块随 Flow 版本固定，办理人员在进入节点时解析为候选人。 */
    public record Stage(
            @Schema(description="流程内稳定阶段编码") String code,
            @Schema(description="阶段展示名称") String name,
            @Schema(description="是否允许阶段管理人手动推进；关闭时仅配置的事件可推进") boolean manualEnabled,
            @Schema(description="阶段管理人员账号主键；可关联补充记录，手动推进还受 manualEnabled 控制") List<Long> managerAccountIds,
            @Schema(description="本阶段允许关联补充资料的模块主键") List<Long> relatedModuleIds,
            @Schema(description="离开本阶段时可选发起的审批定义主键") Long examineId) {}

    /** 审批阶段须分别配置通过、拒绝和取消的线路，目标为空表示结束。 */
    public record Route(
            @Schema(description="来源阶段编码") String sourceCode,
            @Schema(description="目标阶段编码；空表示结束") String targetCode,
            @Schema(description="审批结果：COMPLETED、REJECTED 或 CANCELLED；无审批阶段为空") String approvalStatus,
            @Schema(description="命中该业务事件时自动推进；手动线路为空") String autoEventCode,
            @Schema(description="业务字段线路条件") JsonNode condition,
            @Schema(description="同阶段条件线路匹配顺序") Integer priorityNo,
            @Schema(description="命中后是否停止匹配后续线路") Boolean exclusiveFlag) {}

    /** 当前模块阶段配置及所用发布版本。 */
    public record ConfigView(
            @Schema(description="模块主键") Long moduleId,
            @Schema(description="Flow 定义主键") Long flowId,
            @Schema(description="当前 Flow 发布版本主键") Long flowVersionId,
            @Schema(description="触发绑定主键") Long bindingId,
            @Schema(description="当前阶段配置") SaveRequest configuration) {}

    /** 某条业务记录当前或历史阶段及该阶段关联的补充记录。 */
    public record RecordStageView(
            @Schema(description="Flow 实例主键") Long flowExampleId,
            @Schema(description="使用的 Flow 发布版本主键") Long flowVersionId,
            @Schema(description="阶段编码") String stageCode,
            @Schema(description="阶段名称") String stageName,
            @Schema(description="是否为当前待办阶段") boolean current,
            @Schema(description="当前阶段待办主键；历史阶段为空") Long taskId,
            @Schema(description="本阶段已关联的补充记录") List<RelatedRecord> relatedRecords) {}

    /** 补充记录的稳定关联主键与当前可读标题。 */
    public record RelatedRecord(
            @Schema(description="阶段关联主键") Long relationId,
            @Schema(description="目标模块主键") Long moduleId,
            @Schema(description="目标记录主键") Long recordId,
            @Schema(description="目标记录标题") String name) {}

    /** 关联已有目标记录到当前阶段。 */
    public record AttachRequest(@Schema(description="补充记录主键") Long targetRecordId) {}
}
