package com.unique.module.mapper;

import com.unique.core.entity.user.bo.SimpleUserRole;
import com.unique.module.entity.po.ModuleRoleUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import com.unique.core.common.BasePage;
import com.unique.core.entity.base.bo.SearchBO;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 用户角色对应关系表 Mapper 接口
 * </p>
 *
 * @author UNIQUE
 * @since 2024-08-28
 */
public interface ModuleRoleUserMapper extends BaseMapper<ModuleRoleUser> {

 BasePage<ModuleRoleUser> queryPageList(BasePage<Object> parse, @Param("search") SearchBO search);

 List<SimpleUserRole> queryDataType(@Param("moduleId")Long moduleId, @Param("userIds")List<Long> userIds);
}
