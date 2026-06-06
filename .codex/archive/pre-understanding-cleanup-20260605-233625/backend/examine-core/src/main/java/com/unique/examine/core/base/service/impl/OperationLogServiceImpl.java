package com.unique.examine.core.base.service.impl;

import com.unique.examine.core.base.entity.OperationLog;
import com.unique.examine.core.base.mapper.OperationLogMapper;
import com.unique.examine.core.base.service.IOperationLogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 审计操作日志 服务实现类
 * </p>
 *
 * @author codex
 * @since 2026-06-05
 */
@Service
public class OperationLogServiceImpl extends ServiceImpl<OperationLogMapper, OperationLog> implements IOperationLogService {

}
