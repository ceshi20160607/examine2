package com.unique.module.mapper;

import com.unique.core.entity.user.bo.SimpleDept;
import com.unique.module.entity.po.ModuleDept;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import com.unique.core.common.BasePage;
import com.unique.core.entity.base.bo.SearchBO;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 部门表 Mapper 接口
 * </p>
 *
 * @author UNIQUE
 * @since 2024-08-28
 */
public interface ModuleDeptMapper extends BaseMapper<ModuleDept> {

 BasePage<ModuleDept> queryPageList(BasePage<Object> parse, @Param("search") SearchBO search);

 List<SimpleDept> queryAllDepts(@Param("moduleId")Long moduleId);

 List<SimpleDept> queryDataDepts(@Param("moduleId")Long moduleId,@Param("userId") Long userId);
}
