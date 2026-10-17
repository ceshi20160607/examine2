package com.unique.unexamine.platform.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 平台成员角色关系
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_plat_member_role")
public class PlatformMemberRole {

    /**
     * 平台成员角色关系主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属平台标识
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 获授权的平台成员标识
     */
    @TableField("member_id")
    private Long memberId;

    /**
     * 授予的平台角色标识
     */
    @TableField("role_id")
    private Long roleId;

    /**
     * 平台成员角色关系创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPlatformId() {
        return platformId;
    }

    public void setPlatformId(Long platformId) {
        this.platformId = platformId;
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

    @Override
    public String toString() {
        return "PlatformMemberRole{" +
            "id = " + id +
            ", platformId = " + platformId +
            ", memberId = " + memberId +
            ", roleId = " + roleId +
            ", createTime = " + createTime +
            "}";
    }
}
