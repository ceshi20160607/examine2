package com.unique.unexamine.foundation.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unique.unexamine.foundation.base.entity.CoreIdempotencyRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 业务操作幂等请求及首次执行结果 Mapper 接口
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@Mapper
public interface CoreIdempotencyRecordMapper extends BaseMapper<CoreIdempotencyRecord> {

}
