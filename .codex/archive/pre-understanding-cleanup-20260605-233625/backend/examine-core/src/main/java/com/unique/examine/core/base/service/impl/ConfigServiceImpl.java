package com.unique.examine.core.base.service.impl;

import com.unique.examine.core.base.entity.Config;
import com.unique.examine.core.base.mapper.ConfigMapper;
import com.unique.examine.core.base.service.IConfigService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 系统配置 服务实现类
 * </p>
 *
 * @author codex
 * @since 2026-06-05
 */
@Service
public class ConfigServiceImpl extends ServiceImpl<ConfigMapper, Config> implements IConfigService {

}
