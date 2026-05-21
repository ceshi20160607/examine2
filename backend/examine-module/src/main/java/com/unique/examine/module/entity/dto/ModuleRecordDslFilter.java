package com.unique.examine.module.entity.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ModuleRecordDslFilter {

    /**
     * 字段：保留字 id / createTime / updateTime 走主表；否则视为 field_code（EAV），与 un_module_field.field_code 对齐。
     */
    private String field;

    /**
     * 操作符（白名单）：eq/in/like/gte/lte
     */
    private String op;

    /**
     * 单值（eq/like/gte/lte）
     */
    private Object value;

    /**
     * 多值（in）
     */
    private List<Object> values;

    /** 服务端解析：NONE | NUM | DT，用于 EAV typed 列条件（不接受客户端传入）。 */
    @JsonIgnore
    private String typedKind = "NONE";

    @JsonIgnore
    private BigDecimal valueNum;

    @JsonIgnore
    private LocalDateTime valueDt;
}

