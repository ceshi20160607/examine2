package com.unique.examine.base.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.base.entity.WorkflowVersion;
import com.unique.examine.base.mapper.WorkflowVersionMapper;
import com.unique.examine.base.service.IWorkflowVersionService;
import org.springframework.stereotype.Service;

@Service
public class WorkflowVersionServiceImpl extends ServiceImpl<WorkflowVersionMapper, WorkflowVersion> implements IWorkflowVersionService {
}
