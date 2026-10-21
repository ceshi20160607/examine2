package com.unique.unexamine.plat.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 平台成员角色关系
 * </p>
 *
 * @author Template Base
 * @since 2026-10-21
 */
@TableName("un_plat_member_role")
public class UnPlatMemberRole implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 平台成员角色关系主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 平台成员主键
     */
    @TableField("member_id")
    private Long memberId;

    /**
     * 平台角色主键
     */
    @TableField("role_id")
    private Long roleId;

    /**
     * 授权时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
