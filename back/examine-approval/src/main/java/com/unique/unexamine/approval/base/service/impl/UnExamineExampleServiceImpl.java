package com.unique.unexamine.approval.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.approval.base.entity.UnExamineExample;
import com.unique.unexamine.approval.base.mapper.UnExamineExampleMapper;
import com.unique.unexamine.approval.base.service.IUnExamineExampleService;
import org.springframework.stereotype.Service;

/** UnExamineExample 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnExamineExampleServiceImpl extends ServiceImpl<UnExamineExampleMapper, UnExamineExample> implements IUnExamineExampleService {
}
