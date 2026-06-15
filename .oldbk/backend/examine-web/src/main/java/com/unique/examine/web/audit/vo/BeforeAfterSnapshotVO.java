package com.unique.examine.web.audit.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 前后快照。
 */
@Data
@Builder
@Schema(description = "前后快照")
public class BeforeAfterSnapshotVO {

    @Schema(description = "变更前快照 JSON")
    private String beforeSnapshotJson;

    @Schema(description = "变更后快照 JSON")
    private String afterSnapshotJson;
}
