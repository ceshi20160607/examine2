package com.unique.unexamine.flow.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.flow.base.entity.UnFlow;
import com.unique.unexamine.flow.base.mapper.UnFlowMapper;
import com.unique.unexamine.flow.base.service.IUnFlowService;
import org.springframework.stereotype.Service;

/** UnFlow 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnFlowServiceImpl extends ServiceImpl<UnFlowMapper, UnFlow> implements IUnFlowService {
}
