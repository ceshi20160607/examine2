package com.unique.examine.core.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unique.examine.core.base.entity.OutboxEvent;
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
public interface CoreOutboxEventMapper extends BaseMapper<OutboxEvent> {

}
