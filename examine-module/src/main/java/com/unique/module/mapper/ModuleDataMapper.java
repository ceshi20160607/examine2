package com.unique.module.mapper;

import com.unique.module.entity.po.ModuleData;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import com.unique.core.common.BasePage;
import com.unique.core.entity.base.bo.SearchBO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

/**
 * <p>
 * 用户模块的数据权限 Mapper 接口
 * </p>
 *
 * @author UNIQUE
 * @since 2024-07-01
 */
public interface ModuleDataMapper extends BaseMapper<ModuleData> {

 BasePage<Map<String, Object>> queryPageList(BasePage<Object> parse, @Param("search") SearchBO search);

}
