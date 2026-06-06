package com.unique.examine.plat.base.service.impl;

import com.unique.examine.plat.base.entity.Tenant;
import com.unique.examine.plat.base.mapper.TenantMapper;
import com.unique.examine.plat.base.service.ITenantService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 平台租户 服务实现类
 * </p>
 *
 * @author codex
 * @since 2026-06-05
 */
@Service
public class TenantServiceImpl extends ServiceImpl<TenantMapper, Tenant> implements ITenantService {

}
