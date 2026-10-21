package com.unique.unexamine.approval.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.approval.base.entity.UnExamineExampleNode;
import com.unique.unexamine.approval.base.mapper.UnExamineExampleNodeMapper;
import com.unique.unexamine.approval.base.service.IUnExamineExampleNodeService;
import org.springframework.stereotype.Service;

/** UnExamineExampleNode 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnExamineExampleNodeServiceImpl extends ServiceImpl<UnExamineExampleNodeMapper, UnExamineExampleNode> implements IUnExamineExampleNodeService {
}
