package com.unique.module.mapper;

import com.unique.module.entity.po.ModuleRoleMenu;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import com.unique.core.common.BasePage;
import com.unique.core.entity.base.bo.SearchBO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

/**
 * <p>
 * 模块的角色对应菜单权限 Mapper 接口
 * </p>
 *
 * @author UNIQUE
 * @since 2024-07-02
 */
public interface ModuleRoleMenuMapper extends BaseMapper<ModuleRoleMenu> {

 BasePage<Map<String, Object>> queryPageList(BasePage<Object> parse, @Param("search") SearchBO search);

}
