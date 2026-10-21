package com.unique.unexamine.approval.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.approval.base.entity.UnExamineConfigUser;
import com.unique.unexamine.approval.base.mapper.UnExamineConfigUserMapper;
import com.unique.unexamine.approval.base.service.IUnExamineConfigUserService;
import org.springframework.stereotype.Service;

/** UnExamineConfigUser 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnExamineConfigUserServiceImpl extends ServiceImpl<UnExamineConfigUserMapper, UnExamineConfigUser> implements IUnExamineConfigUserService {
}
