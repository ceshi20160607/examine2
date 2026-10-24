package com.unique.unexamine.sys.module.manage.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** ModuleRecord 的请求与返回模型。 */
public final class ModuleRecordDto {
    private ModuleRecordDto() {}

    /** 表单提交主键为空时新增，传主键时更新同模块记录。 */
    public record SaveRecordRequest(Long id, Long ownerAccountId,
                                    @NotNull Map<String, Object> values) {}

    /** `queryById` 只展示基础记录，不拼接表单布局。 */
    public record RecordView(Long id, String name, String status, Long ownerAccountId,
                             Long createAccountId, LocalDateTime createTime,
                             Long sourceTenantId,boolean shared) {}

    /** 批量删除的记录标识；每条记录仍逐条检查当前动作的数据权限。 */
    public record DeleteRecordsRequest(@NotEmpty List<@NotNull Long> ids) {}

    /** 详情字段按原配置绑定值。 */
    public record FieldView(String fieldName,Map<String,Object> configuration,Object value) {}

    /** `information` 复用当前保存的表单配置并填入基础及扩展值。 */
    public record RecordInformation(Long id,Long sourceTenantId,boolean shared,List<FieldView> fields) {}

    /** 输入字段只允许动作指定的业务字段；归属转移使用单独的负责人参数。 */
    public record ExecuteRequest(Map<String,Object> values,Long ownerAccountId) {}

    /** 成功结果仅返回关联标识，后续跳转仍校验目标记录读取权限。 */
    public record ActionResult(Long recordId,Long targetModuleId,Long targetRecordId) {}

    /** 运行态按钮与页面共用菜单身份。 */
    public record RuntimeAction(Long menuId,String name,String type,String position) {}

    /** 日志保留操作发生时的字段名称、业务值和字典名称。 */
    public record FieldChange(Long fieldId,String fieldCode,String fieldName,Object before,Object after,
                               Map<String,String> beforeDisplay,Map<String,String> afterDisplay) {}

    /** 配置迁移与普通字段更新使用不同 actionCode，此处保存执行上下文。 */
    public record AuditDetail(String actionName,String operatorName,List<FieldChange> changes,
                               Long relatedModuleId,Long relatedRecordId,String message) {}

    /** 返回的字段变化已经按当前字段读取许可过滤。 */
    public record OperationView(Long id,String operationType,Long operatorAccountId,LocalDateTime createTime,AuditDetail detail) {}
}
