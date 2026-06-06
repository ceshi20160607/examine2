package com.unique.examine.base.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.base.entity.WorkflowInstance;
import com.unique.examine.base.mapper.WorkflowInstanceMapper;
import com.unique.examine.base.service.IWorkflowInstanceService;
import org.springframework.stereotype.Service;

@Service
public class WorkflowInstanceServiceImpl extends ServiceImpl<WorkflowInstanceMapper, WorkflowInstance> implements IWorkflowInstanceService {
}
