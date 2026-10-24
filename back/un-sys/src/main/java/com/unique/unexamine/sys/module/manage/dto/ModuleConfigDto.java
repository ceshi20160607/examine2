package com.unique.unexamine.sys.module.manage.dto;

import jakarta.validation.constraints.*;
import java.util.*;

/** ModuleConfig 的请求与返回模型。 */
public final class ModuleConfigDto {
    private ModuleConfigDto() {}

    /** 创建运行态导航模块组。 */
    public record CreateGroupRequest(@NotBlank @Size(max=80) String code,@NotBlank @Size(max=100) String name) {}

    /** 模块必须归属主租户已配置的模块组。 */
    public record CreateModuleRequest(@NotNull Long groupId,@NotBlank @Size(max=80) String code,
                                      @NotBlank @Size(max=100) String name) {}

    /** 字段保存同时携带必要的旧选项到新选项映射，不另存一份待发布草稿。 */
    public record SaveFieldRequest(@NotBlank @Size(max=80) String fieldCode,
            @NotBlank @Size(max=100) String fieldName,@NotBlank String fieldType,
            boolean required,int sortNo,@NotNull Map<String,Object> layout,
            @NotNull Map<String,Object> config,Map<Long,Long> optionMappings) {}

    /** 编辑名称、说明与启停状态不改变模块归属或稳定编码。 */
    public record UpdateModuleRequest(@NotBlank @Size(max=100) String name,
                                      @Size(max=500) String description,boolean enabled) {}

    /** 当前有效模块信息，不暴露产品配置版本。 */
    public record ModuleView(Long id,String code,String name,String status) {}

    /** 页面字段携带原有布局配置。 */
    public record FieldConfig(String fieldName,Map<String,Object> configuration) {}

    /** 新建、编辑和详情读取同一份当前保存布局。 */
    public record FormView(Long moduleId,List<FieldConfig> fields) {}

    /** 菜单与角色字段授权引用稳定字段 ID。 */
    public record FieldDefinitionView(Long fieldId,String fieldCode,Map<String,Object> configuration) {}

    /** 导航只返回当前登录人具有列表动作权限的模块。 */
    public record NavigationModule(Long id,String code,String name,Long ownerTenantId,boolean inherited) {}

public record NavigationGroup(Long id,String code,String name,List<NavigationModule> modules) {}
}
