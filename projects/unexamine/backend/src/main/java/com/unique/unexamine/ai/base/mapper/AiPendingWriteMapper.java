package com.unique.unexamine.ai.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unique.unexamine.ai.base.entity.AiPendingWrite;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 人工确认前不得写入正式业务表的 AI 提议 Mapper 接口
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@Mapper
public interface AiPendingWriteMapper extends BaseMapper<AiPendingWrite> {

}
