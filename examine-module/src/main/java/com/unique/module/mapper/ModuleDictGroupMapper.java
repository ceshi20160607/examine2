package com.unique.module.mapper;

import com.unique.module.entity.po.ModuleDictGroup;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import com.unique.core.common.BasePage;
import com.unique.core.entity.base.bo.SearchBO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

/**
 * <p>
 * 数据字典组表 Mapper 接口
 * </p>
 *
 * @author UNIQUE
 * @since 2024-08-19
 */
public interface ModuleDictGroupMapper extends BaseMapper<ModuleDictGroup> {

 BasePage<Map<String, Object>> queryPageList(BasePage<Object> parse, @Param("search") SearchBO search);

}
