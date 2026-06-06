package com.unique.examine.app.base.service.impl;

import com.unique.examine.app.base.entity.Application;
import com.unique.examine.app.base.mapper.ApplicationMapper;
import com.unique.examine.app.base.service.IApplicationService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 可配置应用 服务实现类
 * </p>
 *
 * @author codex
 * @since 2026-06-05
 */
@Service
public class ApplicationServiceImpl extends ServiceImpl<ApplicationMapper, Application> implements IApplicationService {

}
