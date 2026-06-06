package com.unique.examine.base.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.base.entity.OpenapiIpWhitelist;
import com.unique.examine.base.mapper.OpenapiIpWhitelistMapper;
import com.unique.examine.base.service.IOpenapiIpWhitelistService;
import org.springframework.stereotype.Service;

@Service
public class OpenapiIpWhitelistServiceImpl extends ServiceImpl<OpenapiIpWhitelistMapper, OpenapiIpWhitelist> implements IOpenapiIpWhitelistService {
}
