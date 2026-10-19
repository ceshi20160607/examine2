package com.unique.unexamine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 工作日志评论与管理反馈
 * </p>
 *
 * @author Template Base
 * @since 2026-10-19
 */
@TableName("un_work_daily_comment")
public class UnWorkDailyComment implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 工作日志评论主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属每日工作日志主键
     */
    @TableField("daily_id")
    private Long dailyId;

    /**
     * 评论的系统成员主键
     */
    @TableField("member_id")
    private Long memberId;

    /**
     * 评论或管理反馈内容
     */
    @TableField("content")
    private String content;

    /**
     * 评论时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDailyId() {
        return dailyId;
    }

    public void setDailyId(Long dailyId) {
        this.dailyId = dailyId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
