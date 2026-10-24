package com.unique.unexamine.sys.module.manage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Dictionary 的请求与返回模型。 */
public final class DictionaryDto {
    private DictionaryDto() {}

    /** 新增基础项时确定稳定分类、基础值和父级关系。 */
    public record CreateRequest(@NotBlank @Size(max=80) @Pattern(regexp="[A-Za-z0-9_]+") String dictCode,
                                @NotBlank @Size(max=100) String value,@NotBlank @Size(max=100) String name,
                                Long parentId,Integer sortNo,String color,String icon,Boolean defaultFlag) {}

    /** 修改已存在基础项的名称与展示资料，不接受稳定值和父级的覆盖。 */
    public record UpdateRequest(@NotBlank @Size(max=100) String name,
                                Integer sortNo,String color,String icon,Boolean defaultFlag) {}

    /** 字段引用 id，value 是例如 1、0 的基础值，name 为当前显示名称。 */
    public record Item(Long id,String dictCode,String value,String name,Long parentId,
                       int depthLevel,int sortNo,String color,String icon,boolean defaultFlag) {}
}
