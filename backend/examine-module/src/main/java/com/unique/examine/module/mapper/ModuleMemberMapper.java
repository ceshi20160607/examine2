package com.unique.examine.module.mapper;

import com.unique.examine.module.entity.po.ModuleMember;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * 应用成员 Mapper 接口
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-14
 */
public interface ModuleMemberMapper extends BaseMapper<ModuleMember> {

    /**
     * 解析当前成员在 (system,tenant) 下的 module 权限键（按成员 role → role_menu_perm → menu.perm_key）。
     */
    List<String> selectModulePermKeysByPlat(
            @Param("systemId") long systemId,
            @Param("tenantId") long tenantId,
            @Param("platId") long platId
    );

    List<Long> selectPlatIdsByRole(
            @Param("systemId") long systemId,
            @Param("tenantId") long tenantId,
            @Param("roleId") long roleId
    );
}
