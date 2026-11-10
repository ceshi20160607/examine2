package com.unique.unexamine.sys.module.manage.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.media.Schema;

/** ModuleRecord 的请求与返回模型。 */
public final class ModuleRecordDto {
    private ModuleRecordDto() {}

    /** 草稿允许缺少必填字段，提交时校验完整表单并触发配置的流程。 */
    public record SaveRecordRequest(Long id, Long ownerAccountId,
                                    @NotNull Map<String, Object> values,
                                    @Schema(description="DRAFT 保存草稿；SUBMIT 完整提交") String mode) {
        public SaveRecordRequest(Long id,Long ownerAccountId,Map<String,Object> values) {
            this(id,ownerAccountId,values,"SUBMIT");
        }
    }

    /** `queryById` 只展示基础记录，不拼接表单布局。 */
    public record RecordView(Long id, String name, String status,
                             @Schema(description="当前审批状态，0 为未提交") Integer checkStatus,Long ownerAccountId,
                             Long createAccountId, LocalDateTime createTime,
                             Long sourceTenantId,boolean shared) {}

    /** 批量删除的记录标识；每条记录仍逐条检查当前动作的数据权限。 */
    public record DeleteRecordsRequest(@NotEmpty List<@NotNull Long> ids) {}

    /** 详情字段按原配置绑定值。 */
    public record FieldView(String fieldName,Map<String,Object> configuration,Object value) {}

    /** `information` 复用当前保存的表单配置并填入基础及扩展值。 */
    public record RecordInformation(Long id,Long sourceTenantId,boolean shared,
                                    @Schema(description="模块业务状态编码") String status,
                                    @Schema(description="当前审批状态，0 为未提交") Integer checkStatus,
                                    List<FieldView> fields,
                                    @Schema(description="页面配置的各审批状态字段当前值") Map<String,String> approvalStatuses) {}

    /** 输入字段只允许动作指定的业务字段；归属转移使用单独的负责人参数。 */
    public record ExecuteRequest(
            @Schema(description="动作提交的字段 key 与业务值；仅可提交动作配置允许的字段") Map<String,Object> values,
            @Schema(description="转移动作的目标负责人账号主键，其他动作留空") Long ownerAccountId) {}

    /** 成功结果仅返回关联标识，后续跳转仍校验目标记录读取权限。 */
    public record ActionResult(
            @Schema(description="执行动作的原业务记录主键") Long recordId,
            @Schema(description="转化动作创建的目标模块主键，其他动作为空") Long targetModuleId,
            @Schema(description="转化动作创建的目标记录主键，其他动作为空") Long targetRecordId) {}

    /** 运行态按钮与页面共用菜单身份。 */
    public record RuntimeAction(Long menuId,String name,String type,String position) {}

    /** 日志保留操作发生时的字段名称、业务值和字典名称。 */
    public record FieldChange(Long fieldId,String fieldKey,String fieldName,Object before,Object after,
                               Map<String,String> beforeDisplay,Map<String,String> afterDisplay) {}

    /** 配置迁移与普通字段更新使用不同 actionCode，此处保存执行上下文。 */
    public record AuditDetail(String actionName,String operatorName,List<FieldChange> changes,
                               Long relatedModuleId,Long relatedRecordId,String message) {}

    /** 返回的字段变化已经按当前字段读取许可过滤。 */
    public record OperationView(Long id,String operationType,Long operatorAccountId,LocalDateTime createTime,AuditDetail detail) {}
}
