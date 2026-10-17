package com.unique.unexamine.operations.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unique.unexamine.operations.base.entity.OpsRequestLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 不含敏感请求内容的调用观测与追踪记录 Mapper 接口
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@Mapper
public interface OpsRequestLogMapper extends BaseMapper<OpsRequestLog> {

}
