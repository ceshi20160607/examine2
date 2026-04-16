package com.unique.examine.module.mapper;

import com.unique.examine.module.entity.po.ModuleMenu;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * 应用菜单（权限与接口门统一挂菜单） Mapper 接口
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-14
 */
public interface ModuleMenuMapper extends BaseMapper<ModuleMenu> {

    List<ModuleMenu> selectMenusWithApiPatternForAcl(@Param("appId") Long appId);
}
