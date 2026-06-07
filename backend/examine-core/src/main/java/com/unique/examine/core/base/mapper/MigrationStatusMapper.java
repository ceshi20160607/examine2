package com.unique.examine.core.base.mapper;

import com.unique.examine.core.base.entity.MigrationStatus;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * DB migration 状态查询落点。 基础 Mapper。
 *
 * @author examine-generator
 * @since generated
 */
@Mapper
public interface MigrationStatusMapper extends BaseMapper<MigrationStatus> {
}
