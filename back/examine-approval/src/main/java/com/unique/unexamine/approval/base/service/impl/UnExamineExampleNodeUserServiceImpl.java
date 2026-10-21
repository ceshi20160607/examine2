package com.unique.unexamine.approval.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.approval.base.entity.UnExamineExampleNodeUser;
import com.unique.unexamine.approval.base.mapper.UnExamineExampleNodeUserMapper;
import com.unique.unexamine.approval.base.service.IUnExamineExampleNodeUserService;
import org.springframework.stereotype.Service;

/** UnExamineExampleNodeUser 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnExamineExampleNodeUserServiceImpl extends ServiceImpl<UnExamineExampleNodeUserMapper, UnExamineExampleNodeUser> implements IUnExamineExampleNodeUserService {
}
