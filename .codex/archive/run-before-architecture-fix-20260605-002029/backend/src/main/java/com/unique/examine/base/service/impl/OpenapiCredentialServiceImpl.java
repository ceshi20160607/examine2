package com.unique.examine.base.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.base.entity.OpenapiCredential;
import com.unique.examine.base.mapper.OpenapiCredentialMapper;
import com.unique.examine.base.service.IOpenapiCredentialService;
import org.springframework.stereotype.Service;

@Service
public class OpenapiCredentialServiceImpl extends ServiceImpl<OpenapiCredentialMapper, OpenapiCredential> implements IOpenapiCredentialService {
}
