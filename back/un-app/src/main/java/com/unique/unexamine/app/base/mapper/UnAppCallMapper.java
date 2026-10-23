package com.unique.unexamine.app.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unique.unexamine.app.base.entity.UnAppCall;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 应用调用幂等与审计记录 Mapper 接口
 * </p>
 *
 * @author Template Base
 * @since 2026-10-23
 */
@Mapper
public interface UnAppCallMapper extends BaseMapper<UnAppCall> {

}
