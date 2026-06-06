package com.unique.examine.base.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.base.entity.Tenant;
import com.unique.examine.base.mapper.TenantMapper;
import com.unique.examine.base.service.ITenantService;
import org.springframework.stereotype.Service;

@Service
public class TenantServiceImpl extends ServiceImpl<TenantMapper, Tenant> implements ITenantService {
}
