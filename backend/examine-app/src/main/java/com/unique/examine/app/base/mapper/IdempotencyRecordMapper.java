package com.unique.examine.app.base.mapper;

import com.unique.examine.app.base.entity.IdempotencyRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 外部写接口幂等记录、请求摘要和结果快照。 基础 Mapper。
 *
 * @author examine-generator
 * @since generated
 */
@Mapper
public interface IdempotencyRecordMapper extends BaseMapper<IdempotencyRecord> {
}
