package com.unique.examine.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "OpenAPI IP白名单保存入参")
public class OpenApiIpSaveBO {
    @NotNull
    @Schema(description = "OpenAPI应用主键")
    private Long clientPk;
    @Schema(description = "单条白名单IP或网段")
    private String ipValue;
    @Schema(description = "批量白名单IP或网段；与 ipValue 至少传一个")
    private List<String> ipList;
}
