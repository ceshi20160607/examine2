package com.unique.examine.app.base.service.impl;

import com.unique.examine.app.base.entity.AccessLog;
import com.unique.examine.app.base.mapper.AccessLogMapper;
import com.unique.examine.app.base.service.IAccessLogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * OpenAPI 调用日志 服务实现类
 * </p>
 *
 * @author codex
 * @since 2026-06-05
 */
@Service
public class AccessLogServiceImpl extends ServiceImpl<AccessLogMapper, AccessLog> implements IAccessLogService {

}
