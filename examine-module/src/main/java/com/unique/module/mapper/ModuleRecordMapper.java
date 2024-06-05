package com.unique.module.mapper;

import com.unique.module.entity.po.ModuleRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import com.unique.core.common.BasePage;
import com.unique.core.entity.base.bo.SearchBO;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 主数据基础表 Mapper 接口
 * </p>
 *
 * @author UNIQUE
 * @since 2024-05-28
 */
public interface ModuleRecordMapper extends BaseMapper<ModuleRecord> {

    BasePage<Map<String, Object>> queryPageList(BasePage<Object> parse, @Param("search") SearchBO search);

    void updateNullByFieldNameWithModuleId(@Param("removeBaseFieldNames") List<String> removeBaseFieldNames, @Param("moduleId") Long moduleId);
}
