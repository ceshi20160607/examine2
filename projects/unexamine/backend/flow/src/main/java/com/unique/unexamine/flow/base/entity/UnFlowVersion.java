package com.unique.unexamine.flow.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 不可变流程发布版本
 * </p>
 *
 * @author Template Base
 * @since 2026-10-19
 */
@TableName("un_flow_version")
public class UnFlowVersion implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 流程发布版本主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属流程定义主键
     */
    @TableField("flow_id")
    private Long flowId;

    /**
     * 流程内递增版本号
     */
    @TableField("version_no")
    private Integer versionNo;

    /**
     * 发布时完整节点与连线快照
     */
    @TableField("definition_snapshot")
    private String definitionSnapshot;

    /**
     * 发布成员主键
     */
    @TableField("publish_member_id")
    private Long publishMemberId;

    /**
     * 发布时间
     */
    @TableField("publish_time")
    private LocalDateTime publishTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getFlowId() {
        return flowId;
    }

    public void setFlowId(Long flowId) {
        this.flowId = flowId;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public String getDefinitionSnapshot() {
        return definitionSnapshot;
    }

    public void setDefinitionSnapshot(String definitionSnapshot) {
        this.definitionSnapshot = definitionSnapshot;
    }

    public Long getPublishMemberId() {
        return publishMemberId;
    }

    public void setPublishMemberId(Long publishMemberId) {
        this.publishMemberId = publishMemberId;
    }

    public LocalDateTime getPublishTime() {
        return publishTime;
    }

    public void setPublishTime(LocalDateTime publishTime) {
        this.publishTime = publishTime;
    }
}
