package com.unique.examine.base.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.base.entity.OpenapiIdempotency;
import com.unique.examine.base.mapper.OpenapiIdempotencyMapper;
import com.unique.examine.base.service.IOpenapiIdempotencyService;
import org.springframework.stereotype.Service;

@Service
public class OpenapiIdempotencyServiceImpl extends ServiceImpl<OpenapiIdempotencyMapper, OpenapiIdempotency> implements IOpenapiIdempotencyService {
}
