package com.unique.examine.flow.base.mapper;

import com.unique.examine.flow.base.entity.Task;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 待办任务、领取、处理和并发版本控制。 基础 Mapper。
 *
 * @author examine-generator
 * @since generated
 */
@Mapper
public interface TaskMapper extends BaseMapper<Task> {
}
