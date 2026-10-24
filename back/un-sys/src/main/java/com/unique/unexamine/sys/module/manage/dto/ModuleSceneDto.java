package com.unique.unexamine.sys.module.manage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** ModuleScene 的请求与返回模型。 */
public final class ModuleSceneDto {
    private ModuleSceneDto() {}

    /** 筛选字段引用模块字段稳定 ID，避免字段改名后变成另一条规则。 */
    public record Condition(Long fieldId,String operator,Object value) {}

    /** 场景中的列按设计器顺序保存；权限变化后运行态再次过滤。 */
    public record Settings(List<Condition> filters,List<Long> columnFieldIds,Long sortFieldId,boolean descending) {}

    /** 个人场景由本人维护，公共场景由租户管理员维护。 */
    public record SaveRequest(Long id,@NotBlank @Size(max=100) String name,boolean personal,
                              List<Condition> filters,List<Long> columnFieldIds,Long sortFieldId,boolean descending) {}

public record SceneView(Long id,String name,boolean personal,Settings settings) {}
}
