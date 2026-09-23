package com.unique.unexamine.runtimedata.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unique.unexamine.runtimedata.base.entity.BizTenantShare;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 同系统跨租户记录及动态模块范围共享授权，不改变数据所有权 Mapper 接口
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@Mapper
public interface BizTenantShareMapper extends BaseMapper<BizTenantShare> {

}
