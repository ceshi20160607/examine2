package com.unique.unexamine.approval.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.approval.base.entity.UnExamineGroup;
import com.unique.unexamine.approval.base.mapper.UnExamineGroupMapper;
import com.unique.unexamine.approval.base.service.IUnExamineGroupService;
import org.springframework.stereotype.Service;

/** UnExamineGroup 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnExamineGroupServiceImpl extends ServiceImpl<UnExamineGroupMapper, UnExamineGroup> implements IUnExamineGroupService {
}
