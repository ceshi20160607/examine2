package com.unique.unexamine.plat.manage.bo;

import com.unique.unexamine.core.bo.SearchBO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 平台操作日志的业务筛选条件；分页和关键词复用 core 的 SearchBO。 */
public class PlatformLogSearchBO extends SearchBO {
    @Schema(description = "操作账号主键")
    private Long accountId;
    @Schema(description = "操作类型编码")
    private String actionCode;
    @Schema(description = "操作对象类型")
    private String objectType;
    @Schema(description = "开始时间，包含")
    private LocalDateTime fromTime;
    @Schema(description = "结束时间，不包含")
    private LocalDateTime toTime;

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public String getActionCode() { return actionCode; }
    public void setActionCode(String actionCode) { this.actionCode = actionCode; }
    public String getObjectType() { return objectType; }
    public void setObjectType(String objectType) { this.objectType = objectType; }
    public LocalDateTime getFromTime() { return fromTime; }
    public void setFromTime(LocalDateTime fromTime) { this.fromTime = fromTime; }
    public LocalDateTime getToTime() { return toTime; }
    public void setToTime(LocalDateTime toTime) { this.toTime = toTime; }
}
