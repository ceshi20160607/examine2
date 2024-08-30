package com.unique.module.mapper;

import com.unique.core.entity.user.bo.SimpleMenu;
import com.unique.module.entity.po.ModuleMenu;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import com.unique.core.common.BasePage;
import com.unique.core.entity.base.bo.SearchBO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 模块菜单功能权限配置表 Mapper 接口
 * </p>
 *
 * @author UNIQUE
 * @since 2024-07-01
 */
public interface ModuleMenuMapper extends BaseMapper<ModuleMenu> {

    BasePage<Map<String, Object>> queryPageList(BasePage<Object> parse, @Param("search") SearchBO search);

    List<SimpleMenu> querySimpleMenu(@Param("moduleId") Long moduleId, @Param("userId") Long userId);
}
