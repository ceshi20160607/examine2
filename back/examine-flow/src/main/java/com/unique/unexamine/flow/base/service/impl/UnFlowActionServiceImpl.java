package com.unique.unexamine.flow.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.flow.base.entity.UnFlowAction;
import com.unique.unexamine.flow.base.mapper.UnFlowActionMapper;
import com.unique.unexamine.flow.base.service.IUnFlowActionService;
import org.springframework.stereotype.Service;

/** UnFlowAction 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnFlowActionServiceImpl extends ServiceImpl<UnFlowActionMapper, UnFlowAction> implements IUnFlowActionService {
}
