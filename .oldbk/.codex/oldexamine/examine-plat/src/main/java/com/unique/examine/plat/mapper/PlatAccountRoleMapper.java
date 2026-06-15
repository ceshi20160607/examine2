package com.unique.examine.plat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unique.examine.plat.entity.po.PlatAccountRole;
import com.unique.examine.plat.entity.po.PlatMenu;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * 平台账号与角色关联 Mapper 接口
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-10
 */
public interface PlatAccountRoleMapper extends BaseMapper<PlatAccountRole> {

    /** RBAC：账号经角色-菜单解析出的权限码 */
    List<String> selectPermCodesByPlatAccountId(@Param("platId") Long platId);

    /** RBAC：账号可见菜单行（多表聚合） */
    List<PlatMenu> selectMenusByPlatAccountId(@Param("platId") Long platId);

    /** RBAC：账号绑定的角色编码 */
    List<String> selectRoleCodesByPlatAccountId(@Param("platId") Long platId);
}
