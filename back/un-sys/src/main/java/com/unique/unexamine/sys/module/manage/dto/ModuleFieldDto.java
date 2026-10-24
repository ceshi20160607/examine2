package com.unique.unexamine.sys.module.manage.dto;

/** ModuleField 的请求与返回模型。 */
public final class ModuleFieldDto {
    private ModuleFieldDto() {}

    /** 精简类型目录只声明基础值类型和当前能否保存使用。 */
    public record TypeView(int code,String name,String valueColumn,boolean available) {}

    /** 人员值对外展示当前成员资料，业务表中始终只保存成员主键。 */
    public record MemberView(Long id,Long accountId,String displayName,String employeeNo,Long departmentId) {}
}
