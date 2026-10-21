package com.unique.unexamine.flow.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.flow.base.entity.UnFlowNode;
import com.unique.unexamine.flow.base.mapper.UnFlowNodeMapper;
import com.unique.unexamine.flow.base.service.IUnFlowNodeService;
import org.springframework.stereotype.Service;

/** UnFlowNode 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnFlowNodeServiceImpl extends ServiceImpl<UnFlowNodeMapper, UnFlowNode> implements IUnFlowNodeService {
}
