package com.unique.unexamine.sys.module.manage.dto;

import com.unique.unexamine.sys.base.entity.UnSysMenu;
import com.unique.unexamine.sys.module.base.entity.*;
import jakarta.validation.constraints.*;
import java.util.*;

/** ModuleMenu 的请求与返回模型。 */
public final class ModuleMenuDto {
    private ModuleMenuDto() {}

    /** 条件字段使用稳定 ID，条件均满足才允许执行。 */
    public record Condition(@NotNull Long fieldId,@NotBlank String operator,Object value) {}

    /** 转化字段映射使用两端稳定字段 ID，不接受数据库列名。 */
    public record Mapping(@NotNull Long sourceFieldId,@NotNull Long targetFieldId) {}

    /** 执行配置的保存载荷；名称同时更新统一菜单。 */
    public record Settings(String name,String type,String position,List<Long> fieldIds,List<Condition> conditions,
                           Long targetModuleId,List<Mapping> mappings) {}

    /** menuId 为空时新建自定义菜单，默认菜单不能更换执行类型。 */
    public record SaveRequest(Long menuId,@NotBlank @Size(max=100) String name,@NotBlank String type,
                              @NotBlank String position,boolean enabled,List<Long> fieldIds,List<Condition> conditions,
                              Long targetModuleId,List<Mapping> mappings) {}

    /** 配置态和运行态共用当前保存的菜单内容。 */
    public record ActionView(Long menuId,String code,String name,boolean enabled,Settings settings) {}

    /** 同一次解析得到的菜单、绑定和动作，避免混用不同模块或租户身份。 */
    public record Definition(UnModule module,UnSysMenu menu,UnModuleMenu binding,UnModuleAction action,Settings settings) {}
}
