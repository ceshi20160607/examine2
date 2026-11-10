package com.unique.unexamine.plat.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unique.unexamine.plat.base.entity.UnPlatRoleMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * <p>
 * 平台角色菜单授权 Mapper 接口
 * </p>
 *
 * @author Template Base
 * @since 2026-10-23
 */
@Mapper
public interface UnPlatRoleMenuMapper extends BaseMapper<UnPlatRoleMenu> {

    /** 撤销后再次授权时恢复同一逻辑记录，避免唯一键冲突。 */
    @Update("UPDATE un_plat_role_menu SET deleted_time = NULL "
            + "WHERE role_id = #{roleId} AND menu_id = #{menuId} AND action_code = #{actionCode}")
    int restoreByGrant(@Param("roleId") Long roleId, @Param("menuId") Long menuId,
                       @Param("actionCode") String actionCode);

}
