package com.unique.examine.app.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 应用发布入参。
 */
@Data
@Schema(description = "应用发布入参")
public class AppPublishBO {

    @Schema(description = "应用 ID")
    private Long appId;

    @Schema(description = "版本号，由配置端指定，数据库唯一约束兜底")
    private Integer versionNo;

    @Schema(description = "版本名称")
    private String versionName;

    @Schema(description = "配置快照 JSON")
    private String snapshotJson;
}
