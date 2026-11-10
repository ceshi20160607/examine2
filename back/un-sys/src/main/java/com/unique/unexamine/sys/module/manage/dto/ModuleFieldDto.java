package com.unique.unexamine.sys.module.manage.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** ModuleField 的请求与返回模型。 */
public final class ModuleFieldDto {
    private ModuleFieldDto() {}

    /** 精简类型目录只声明基础值类型和当前能否保存使用。 */
    public record TypeView(int code,String name,String valueColumn,boolean available) {}

    /** 人员值对外展示当前成员资料，业务表中始终只保存成员主键。 */
    public record MemberView(Long id,Long accountId,String displayName,String employeeNo,Long departmentId) {}

    /** 部门值对外展示当前租户组织资料，业务表中始终只保存部门主键。 */
    public record DepartmentView(@Schema(description = "部门主键") Long id,
                                 @Schema(description = "上级部门主键") Long parentId,
                                 @Schema(description = "部门编码") String code,
                                 @Schema(description = "部门名称") String name) {}
}
