package com.unique.unexamine.sys.module.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unique.unexamine.sys.module.base.entity.UnModuleDict;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 逐条保存值与名称的字典基础项 Mapper 接口
 * </p>
 *
 * @author Template Base
 * @since 2026-10-23
 */
@Mapper
public interface UnModuleDictMapper extends BaseMapper<UnModuleDict> {

}
