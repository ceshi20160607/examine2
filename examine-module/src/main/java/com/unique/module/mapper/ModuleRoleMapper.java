package com.unique.module.mapper;

import com.unique.core.entity.user.bo.SimpleRole;
import com.unique.module.entity.po.ModuleRole;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import com.unique.core.common.BasePage;
import com.unique.core.entity.base.bo.SearchBO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 角色表 Mapper 接口
 * </p>
 *
 * @author UNIQUE
 * @since 2024-07-02
 */
public interface ModuleRoleMapper extends BaseMapper<ModuleRole> {

    BasePage<Map<String, Object>> queryPageList(BasePage<Object> parse, @Param("search") SearchBO search);

    List<SimpleRole> querySimpleRole(@Param("moduleId")Long moduleId,@Param("userId")Long userId);
}
