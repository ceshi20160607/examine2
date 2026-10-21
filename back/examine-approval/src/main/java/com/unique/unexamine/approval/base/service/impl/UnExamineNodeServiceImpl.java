package com.unique.unexamine.approval.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.approval.base.entity.UnExamineNode;
import com.unique.unexamine.approval.base.mapper.UnExamineNodeMapper;
import com.unique.unexamine.approval.base.service.IUnExamineNodeService;
import org.springframework.stereotype.Service;

/** UnExamineNode 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnExamineNodeServiceImpl extends ServiceImpl<UnExamineNodeMapper, UnExamineNode> implements IUnExamineNodeService {
}
