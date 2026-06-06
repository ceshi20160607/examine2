package com.unique.examine.app.base.service.impl;

import com.unique.examine.app.base.entity.Client;
import com.unique.examine.app.base.mapper.ClientMapper;
import com.unique.examine.app.base.service.IClientService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * OpenAPI 客户端 服务实现类
 * </p>
 *
 * @author codex
 * @since 2026-06-05
 */
@Service
public class ClientServiceImpl extends ServiceImpl<ClientMapper, Client> implements IClientService {

}
