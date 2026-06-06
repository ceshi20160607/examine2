package com.unique.examine.base.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.base.entity.OpenapiScope;
import com.unique.examine.base.mapper.OpenapiScopeMapper;
import com.unique.examine.base.service.IOpenapiScopeService;
import org.springframework.stereotype.Service;

@Service
public class OpenapiScopeServiceImpl extends ServiceImpl<OpenapiScopeMapper, OpenapiScope> implements IOpenapiScopeService {
}
