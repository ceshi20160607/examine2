package com.unique.unexamine.approval.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.approval.base.entity.UnExamineNodeField;
import com.unique.unexamine.approval.base.mapper.UnExamineNodeFieldMapper;
import com.unique.unexamine.approval.base.service.IUnExamineNodeFieldService;
import org.springframework.stereotype.Service;

/** UnExamineNodeField 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnExamineNodeFieldServiceImpl extends ServiceImpl<UnExamineNodeFieldMapper, UnExamineNodeField> implements IUnExamineNodeFieldService {
}
