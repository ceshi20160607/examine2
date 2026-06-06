package com.unique.examine.app.base.service.impl;

import com.unique.examine.app.base.entity.Idempotent;
import com.unique.examine.app.base.mapper.IdempotentMapper;
import com.unique.examine.app.base.service.IIdempotentService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * OpenAPI 幂等记录 服务实现类
 * </p>
 *
 * @author codex
 * @since 2026-06-05
 */
@Service
public class IdempotentServiceImpl extends ServiceImpl<IdempotentMapper, Idempotent> implements IIdempotentService {

}
