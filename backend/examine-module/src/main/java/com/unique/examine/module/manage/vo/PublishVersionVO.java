package com.unique.examine.module.manage.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 模块发布版本回显。
 */
@Data
@Builder
@Schema(description = "模块发布版本回显")
public class PublishVersionVO {

    @Schema(description = "发布版本 ID")
    private String publishVersionId;

    @Schema(description = "模块 ID")
    private String moduleId;

    @Schema(description = "版本号")
    private Integer versionNo;

    @Schema(description = "发布状态")
    private String publishStatus;

    @Schema(description = "发布说明")
    private String publishRemark;

    @Schema(description = "发布检查结果 JSON")
    private String checkResult;

    @Schema(description = "发布时间")
    private LocalDateTime publishedAt;
}
