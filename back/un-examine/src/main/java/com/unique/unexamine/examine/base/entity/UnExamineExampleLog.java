package com.unique.unexamine.examine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 审批实例操作与状态日志；每条记录对应一次可审计的审批事件。 */
@TableName("un_examine_example_log")
public class UnExamineExampleLog implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 审批实例日志主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /** 所属审批实例主键。 */
    @TableField("example_id")
    private Long exampleId;
    /** 关联审批实例节点主键。 */
    @TableField("example_node_id")
    private Long exampleNodeId;
    /** 实际操作平台账号主键。 */
    @TableField("account_id")
    private Long accountId;
    /** 审批实例事件编码。 */
    @TableField("event_code")
    private String eventCode;
    /** 审批意见、字段修改、转交、回退或外部调用结果快照。 */
    @TableField("detail_json")
    private String detailJson;
    /** 日志创建时间。 */
    @TableField("create_time")
    private LocalDateTime createTime;
    /** 逻辑删除时间，空表示未删除。 */
    @TableLogic
    @TableField("deleted_time")
    private LocalDateTime deletedTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getExampleId() { return exampleId; }
    public void setExampleId(Long exampleId) { this.exampleId = exampleId; }
    public Long getExampleNodeId() { return exampleNodeId; }
    public void setExampleNodeId(Long exampleNodeId) { this.exampleNodeId = exampleNodeId; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public String getEventCode() { return eventCode; }
    public void setEventCode(String eventCode) { this.eventCode = eventCode; }
    public String getDetailJson() { return detailJson; }
    public void setDetailJson(String detailJson) { this.detailJson = detailJson; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getDeletedTime() { return deletedTime; }
    public void setDeletedTime(LocalDateTime deletedTime) { this.deletedTime = deletedTime; }
}
