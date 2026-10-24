package com.unique.unexamine.work.manage.bo;

import com.unique.unexamine.core.bo.SearchBO;

/** 普通任务和项目任务复用状态查询，项目入口还可限定任务组。 */
public class WorkTaskSearchBO extends SearchBO {
    private String status;
    private Long groupId;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
}
