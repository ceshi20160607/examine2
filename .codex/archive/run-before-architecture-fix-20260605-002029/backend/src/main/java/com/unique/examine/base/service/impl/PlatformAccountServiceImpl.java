package com.unique.examine.base.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.base.entity.PlatformAccount;
import com.unique.examine.base.mapper.PlatformAccountMapper;
import com.unique.examine.base.service.IPlatformAccountService;
import org.springframework.stereotype.Service;

@Service
public class PlatformAccountServiceImpl extends ServiceImpl<PlatformAccountMapper, PlatformAccount> implements IPlatformAccountService {
}
