package com.unique.examine.base.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.base.entity.WorkflowTask;
import com.unique.examine.base.mapper.WorkflowTaskMapper;
import com.unique.examine.base.service.IWorkflowTaskService;
import org.springframework.stereotype.Service;

@Service
public class WorkflowTaskServiceImpl extends ServiceImpl<WorkflowTaskMapper, WorkflowTask> implements IWorkflowTaskService {
}
