package com.unique.examine.app.base.service.impl;

import com.unique.examine.app.base.entity.IpWhitelist;
import com.unique.examine.app.base.mapper.IpWhitelistMapper;
import com.unique.examine.app.base.service.IIpWhitelistService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * OpenAPI IP 白名单 服务实现类
 * </p>
 *
 * @author codex
 * @since 2026-06-05
 */
@Service
public class IpWhitelistServiceImpl extends ServiceImpl<IpWhitelistMapper, IpWhitelist> implements IIpWhitelistService {

}
