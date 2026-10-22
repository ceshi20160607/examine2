package com.unique.unexamine.sys.module.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unique.unexamine.sys.module.base.entity.UnModuleShare;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 跨租户业务数据显式共享 Mapper 接口
 * </p>
 *
 * @author Template Base
 * @since 2026-10-22
 */
@Mapper
public interface UnModuleShareMapper extends BaseMapper<UnModuleShare> {

}
