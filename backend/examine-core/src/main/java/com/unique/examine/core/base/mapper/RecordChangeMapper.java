package com.unique.examine.core.base.mapper;

import com.unique.examine.core.base.entity.RecordChange;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 动态记录字段变更前后快照。 基础 Mapper。
 *
 * @author examine-generator
 * @since 2026-06-06
 */
@Mapper
public interface RecordChangeMapper extends BaseMapper<RecordChange> {
}
