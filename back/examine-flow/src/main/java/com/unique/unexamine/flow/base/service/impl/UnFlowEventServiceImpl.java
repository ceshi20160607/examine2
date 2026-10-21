package com.unique.unexamine.flow.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.flow.base.entity.UnFlowEvent;
import com.unique.unexamine.flow.base.mapper.UnFlowEventMapper;
import com.unique.unexamine.flow.base.service.IUnFlowEventService;
import org.springframework.stereotype.Service;

/** UnFlowEvent 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnFlowEventServiceImpl extends ServiceImpl<UnFlowEventMapper, UnFlowEvent> implements IUnFlowEventService {
}
