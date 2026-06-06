package com.unique.examine.core.base.service.impl;

import com.unique.examine.core.base.entity.LoginLog;
import com.unique.examine.core.base.mapper.LoginLogMapper;
import com.unique.examine.core.base.service.ILoginLogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 系统登录日志 服务实现类
 * </p>
 *
 * @author codex
 * @since 2026-06-05
 */
@Service
public class LoginLogServiceImpl extends ServiceImpl<LoginLogMapper, LoginLog> implements ILoginLogService {

}
