package com.unique.unexamine.sys.manage.bo;

import com.unique.unexamine.core.bo.SearchBO;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

/** 系统日志筛选条件扩展公共分页查询对象。 */
public class SystemLogSearchBO extends SearchBO {
    /** 限定有权管理的租户。 */
    private Long tenantId;
    /** 操作平台账号。 */
    private Long accountId;
    /** 可由日志对象追溯的业务模块。 */
    private Long moduleId;
    /** 操作时间起点，包含。 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime fromTime;
    /** 操作时间终点，不包含。 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime toTime;

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public Long getModuleId() { return moduleId; }
    public void setModuleId(Long moduleId) { this.moduleId = moduleId; }
    public LocalDateTime getFromTime() { return fromTime; }
    public void setFromTime(LocalDateTime fromTime) { this.fromTime = fromTime; }
    public LocalDateTime getToTime() { return toTime; }
    public void setToTime(LocalDateTime toTime) { this.toTime = toTime; }
}
