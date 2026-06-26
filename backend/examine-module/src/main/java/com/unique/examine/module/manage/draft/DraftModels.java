package com.unique.examine.module.manage.draft;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 表单草稿接口模型。
 */
public final class DraftModels {

    private DraftModels() {
    }

    /**
     * 草稿保存请求。
     *
     * @param draftId 草稿编号，首次保存可为空
     * @param recordId 关联记录编号，新建草稿可为空
     * @param fieldValues 字段值快照
     * @param childRows 子表行快照
     * @param attachmentIds 附件草稿编号列表
     * @param clientVersion 前端编辑器版本
     * @param sourceType 草稿来源
     */
    public record DraftSaveRequest(String draftId, String recordId, Map<String, Object> fieldValues,
                                   Map<String, List<Map<String, Object>>> childRows, List<String> attachmentIds,
                                   String clientVersion, String sourceType) {
    }

    /**
     * 草稿字段校验结果。
     *
     * @param fieldCode 字段编码
     * @param message 校验提示
     * @param focusable 是否可定位到字段
     */
    public record DraftValidationIssue(String fieldCode, String message, boolean focusable) {
    }

    /**
     * 草稿详情。
     *
     * @param draftId 草稿编号
     * @param systemId 系统编号
     * @param tenantId 租户编号
     * @param moduleId 模块编号
     * @param recordId 关联记录编号
     * @param fieldValues 字段值快照
     * @param childRows 子表行快照
     * @param attachmentIds 附件草稿编号列表
     * @param validationIssues 草稿校验问题
     * @param permissionSnapshotVersion 权限快照版本
     * @param idempotencyKey 幂等键
     * @param traceId 链路追踪编号
     * @param auditLogId 审计日志编号
     * @param updatedBy 更新人
     * @param updatedAt 更新时间
     */
    public record DraftVO(String draftId, String systemId, String tenantId, String moduleId, String recordId,
                          Map<String, Object> fieldValues, Map<String, List<Map<String, Object>>> childRows,
                          List<String> attachmentIds, List<DraftValidationIssue> validationIssues,
                          String permissionSnapshotVersion, String idempotencyKey, String traceId,
                          String auditLogId, String updatedBy, LocalDateTime updatedAt) {
    }
}
