package com.unique.examine.core.base.service.impl;

import com.unique.examine.core.base.entity.Idempotency;
import com.unique.examine.core.base.mapper.CoreIdempotencyMapper;
import com.unique.examine.core.base.service.IIdempotencyService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author examine-generator
 * @since 2026-07-15
 */
@Service
public class CoreIdempotencyServiceImpl extends ServiceImpl<CoreIdempotencyMapper, Idempotency> implements IIdempotencyService {

}
