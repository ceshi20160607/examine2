package com.unique.unexamine.approval.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.approval.base.entity.UnExamine;
import com.unique.unexamine.approval.base.mapper.UnExamineMapper;
import com.unique.unexamine.approval.base.service.IUnExamineService;
import org.springframework.stereotype.Service;

/** UnExamine 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnExamineServiceImpl extends ServiceImpl<UnExamineMapper, UnExamine> implements IUnExamineService {
}
