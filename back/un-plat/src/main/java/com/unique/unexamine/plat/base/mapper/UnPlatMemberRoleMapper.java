package com.unique.unexamine.plat.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unique.unexamine.plat.base.entity.UnPlatMemberRole;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 平台成员角色关系 Mapper 接口
 * </p>
 *
 * @author Template Base
 * @since 2026-10-23
 */
@Mapper
public interface UnPlatMemberRoleMapper extends BaseMapper<UnPlatMemberRole> {

    /** 撤销后再次授权时恢复同一逻辑记录，避免唯一键冲突。 */
    @Update("UPDATE un_plat_member_role SET deleted_time = NULL "
            + "WHERE member_id = #{memberId} AND role_id = #{roleId}")
    int restoreByPair(@Param("memberId") Long memberId, @Param("roleId") Long roleId);

}
