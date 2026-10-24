package com.unique.unexamine.sys.module.manage.dto;

/** ModuleColumn 的请求与返回模型。 */
public final class ModuleColumnDto {
    private ModuleColumnDto() {}

    /** 保存顺序、显隐、宽度与固定侧，字段身份使用当前保存字段主键。 */
    public record Preference(Long fieldId,int sortNo,Integer widthPx,boolean hidden,String fixedSide) {}
}
