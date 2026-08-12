package com.unique.examine.core.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unique.examine.core.base.entity.Idempotency;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author examine-generator
 * @since 2026-07-15
 */
@Mapper
public interface CoreIdempotencyMapper extends BaseMapper<Idempotency> {

}
