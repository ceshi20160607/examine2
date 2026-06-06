package com.unique.examine.manage.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "文件关联保存入参")
public class FileRelationSaveBO {
    @NotNull private Long systemId;
    @NotNull private Long tenantId;
    @NotNull private Long fileId;
    @NotBlank private String relationType;
    @NotNull private Long relationId;
}

