package com.unique.examine.manage.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "健康检查出参")
public class HealthVO {
    private String serviceStatus;
    private String databaseStatus;
    private String redisStatus;
    private String storageStatus;
    private String scriptVersionStatus;
}

