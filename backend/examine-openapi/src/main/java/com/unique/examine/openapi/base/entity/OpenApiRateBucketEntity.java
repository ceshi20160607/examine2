package com.unique.examine.openapi.base.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

@TableName("un_openapi_rate_bucket")
public class OpenApiRateBucketEntity implements Serializable {
    @TableField("application_id")
    private Long applicationId;
    @TableField("window_start")
    private LocalDateTime windowStart;
    @TableField("request_count")
    private Integer requestCount;
    @TableField("version")
    private Long version;

    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long value) { this.applicationId = value; }
    public LocalDateTime getWindowStart() { return windowStart; }
    public void setWindowStart(LocalDateTime value) { this.windowStart = value; }
    public Integer getRequestCount() { return requestCount; }
    public void setRequestCount(Integer value) { this.requestCount = value; }
    public Long getVersion() { return version; }
    public void setVersion(Long value) { this.version = value; }
}
