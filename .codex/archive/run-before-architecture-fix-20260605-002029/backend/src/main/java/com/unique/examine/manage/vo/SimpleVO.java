package com.unique.examine.manage.vo;

import java.util.Map;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "通用数据出参")
public class SimpleVO {
    @Schema(description = "业务数据字段") private Map<String, Object> fields;
}

