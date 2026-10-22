package com.unique.unexamine.examine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 审批撤回超时等高级配置
 * </p>
 *
 * @author Template Base
 * @since 2026-10-22
 */
@TableName("un_examine_config")
public class UnExamineConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 审批高级配置主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属审批定义主键
     */
    @TableField("examine_id")
    private Long examineId;

    /**
     * 撤回通过超时或重审规则编码
     */
    @TableField("rule_code")
    private String ruleCode;

    /**
     * 草稿适用条件超时兜底等规则
     */
    @TableField("rule_json")
    private String ruleJson;

    /**
     * 最近发布的高级规则快照
     */
    @TableField("published_rule_json")
    private String publishedRuleJson;

    /**
     * 草稿发布或停用状态
     */
    @TableField("status")
    private String status;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getExamineId() {
        return examineId;
    }

    public void setExamineId(Long examineId) {
        this.examineId = examineId;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public String getRuleJson() {
        return ruleJson;
    }

    public void setRuleJson(String ruleJson) {
        this.ruleJson = ruleJson;
    }

    public String getPublishedRuleJson() {
        return publishedRuleJson;
    }

    public void setPublishedRuleJson(String publishedRuleJson) {
        this.publishedRuleJson = publishedRuleJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
