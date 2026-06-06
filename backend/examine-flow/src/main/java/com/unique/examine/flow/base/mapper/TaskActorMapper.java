package com.unique.examine.flow.base.mapper;

import com.unique.examine.flow.base.entity.TaskActor;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务候选人、处理人和转交目标。 基础 Mapper。
 *
 * @author examine-generator
 * @since 2026-06-06
 */
@Mapper
public interface TaskActorMapper extends BaseMapper<TaskActor> {
}
