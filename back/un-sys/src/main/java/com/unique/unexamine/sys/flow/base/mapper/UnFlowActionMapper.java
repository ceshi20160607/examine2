package com.unique.unexamine.sys.flow.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unique.unexamine.sys.flow.base.entity.UnFlowAction;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 流程操作及幂等记录 Mapper 接口
 * </p>
 *
 * @author Template Base
 * @since 2026-10-22
 */
@Mapper
public interface UnFlowActionMapper extends BaseMapper<UnFlowAction> {

}
