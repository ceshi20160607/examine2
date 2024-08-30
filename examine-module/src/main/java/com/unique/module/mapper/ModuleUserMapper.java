package com.unique.module.mapper;

import com.unique.core.entity.user.bo.SimpleUser;
import com.unique.module.entity.po.ModuleUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import com.unique.core.common.BasePage;
import com.unique.core.entity.base.bo.SearchBO;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 用户表 Mapper 接口
 * </p>
 *
 * @author UNIQUE
 * @since 2024-08-28
 */
public interface ModuleUserMapper extends BaseMapper<ModuleUser> {

 BasePage<ModuleUser> queryPageList(BasePage<Object> parse, @Param("search") SearchBO search);

 List<SimpleUser> queryAllUsers( @Param("moduleId")Long moduleId);
}
