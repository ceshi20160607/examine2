package com.unique.examine.manage.vo;

import java.time.LocalDateTime;
import java.util.Map;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "业务记录出参")
public class RecordVO {
    private Long id;
    private Long systemId;
    private Long tenantId;
    private Long appId;
    private Long moduleId;
    private String recordNo;
    private String recordStatus;
    private String processStatus;
    private Long appVersionId;
    private String configSnapshot;
    private Integer isDeleted;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Map<Long, String> values;
}

