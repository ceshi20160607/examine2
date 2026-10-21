package com.unique.unexamine.flow.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.flow.base.entity.UnFlowEdge;
import com.unique.unexamine.flow.base.mapper.UnFlowEdgeMapper;
import com.unique.unexamine.flow.base.service.IUnFlowEdgeService;
import org.springframework.stereotype.Service;

/** UnFlowEdge 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnFlowEdgeServiceImpl extends ServiceImpl<UnFlowEdgeMapper, UnFlowEdge> implements IUnFlowEdgeService {
}
