package com.unique.examine.base.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.base.entity.WorkflowTemplate;
import com.unique.examine.base.mapper.WorkflowTemplateMapper;
import com.unique.examine.base.service.IWorkflowTemplateService;
import org.springframework.stereotype.Service;

@Service
public class WorkflowTemplateServiceImpl extends ServiceImpl<WorkflowTemplateMapper, WorkflowTemplate> implements IWorkflowTemplateService {
}
