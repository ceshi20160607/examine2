package com.unique.examine.module.base.mapper;

import com.unique.examine.module.base.entity.SerialSequence;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 自动编号字段的事务内原子序号段。 基础 Mapper。
 *
 * @author examine-generator
 * @since 2026-06-06
 */
@Mapper
public interface SerialSequenceMapper extends BaseMapper<SerialSequence> {
}
