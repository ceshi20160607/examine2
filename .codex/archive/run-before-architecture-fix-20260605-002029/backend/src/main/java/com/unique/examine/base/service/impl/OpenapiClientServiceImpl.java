package com.unique.examine.base.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.base.entity.OpenapiClient;
import com.unique.examine.base.mapper.OpenapiClientMapper;
import com.unique.examine.base.service.IOpenapiClientService;
import org.springframework.stereotype.Service;

@Service
public class OpenapiClientServiceImpl extends ServiceImpl<OpenapiClientMapper, OpenapiClient> implements IOpenapiClientService {
}
