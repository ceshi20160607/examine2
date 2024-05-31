package com.unique.module.mapper;

import com.unique.module.entity.po.Module;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import com.unique.core.common.BasePage;
import com.unique.core.entity.base.bo.SearchBO;

import org.apache.ibatis.annotations.Param;

import java.util.Map;

/**
 * <p>
 * 模块表 Mapper 接口
 * </p>
 *
 * @author UNIQUE
 * @since 2024-05-28
 */
public interface ModuleMapper extends BaseMapper<Module> {

 BasePage<Map<String, Object>> queryPageList(BasePage<Object> parse, @Param("search") SearchBO search);

}
