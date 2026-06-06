package com.unique.examine.flow.base.service.impl;

import com.unique.examine.flow.base.entity.Task;
import com.unique.examine.flow.base.mapper.TaskMapper;
import com.unique.examine.flow.base.service.ITaskService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 流程任务 服务实现类
 * </p>
 *
 * @author codex
 * @since 2026-06-05
 */
@Service
public class TaskServiceImpl extends ServiceImpl<TaskMapper, Task> implements ITaskService {

}
