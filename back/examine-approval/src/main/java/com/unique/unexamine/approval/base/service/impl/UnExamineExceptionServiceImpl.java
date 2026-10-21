package com.unique.unexamine.approval.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.approval.base.entity.UnExamineException;
import com.unique.unexamine.approval.base.mapper.UnExamineExceptionMapper;
import com.unique.unexamine.approval.base.service.IUnExamineExceptionService;
import org.springframework.stereotype.Service;

/** UnExamineException 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnExamineExceptionServiceImpl extends ServiceImpl<UnExamineExceptionMapper, UnExamineException> implements IUnExamineExceptionService {
}
