package com.unique.examine.core.base.service.impl;

import com.unique.examine.core.base.entity.OutboxEvent;
import com.unique.examine.core.base.mapper.CoreOutboxEventMapper;
import com.unique.examine.core.base.service.IOutboxEventService;
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
public class CoreOutboxEventServiceImpl extends ServiceImpl<CoreOutboxEventMapper, OutboxEvent> implements IOutboxEventService {

}
