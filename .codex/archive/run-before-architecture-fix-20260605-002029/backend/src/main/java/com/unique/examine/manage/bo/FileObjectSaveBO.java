package com.unique.examine.manage.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "文件元数据保存入参")
public class FileObjectSaveBO {
    @NotNull private Long systemId;
    @NotNull private Long tenantId;
    @NotBlank private String storagePath;
    @NotBlank private String fileName;
    @NotNull private Long fileSize;
    @Schema(description = "MIME类型") private String contentType;
}

