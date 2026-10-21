package com.unique.unexamine.approval.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.approval.base.entity.UnExamineNodeUser;
import com.unique.unexamine.approval.base.mapper.UnExamineNodeUserMapper;
import com.unique.unexamine.approval.base.service.IUnExamineNodeUserService;
import org.springframework.stereotype.Service;

/** UnExamineNodeUser 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnExamineNodeUserServiceImpl extends ServiceImpl<UnExamineNodeUserMapper, UnExamineNodeUser> implements IUnExamineNodeUserService {
}
