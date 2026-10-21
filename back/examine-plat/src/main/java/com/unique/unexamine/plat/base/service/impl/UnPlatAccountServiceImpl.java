package com.unique.unexamine.plat.base.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.plat.base.entity.UnPlatAccount;
import com.unique.unexamine.plat.base.mapper.UnPlatAccountMapper;
import com.unique.unexamine.plat.base.service.IUnPlatAccountService;
import org.springframework.stereotype.Service;

/** UnPlatAccount 的服务实现，负责事务入口与 Mapper 的持久化调用。 */
@Service
public class UnPlatAccountServiceImpl extends ServiceImpl<UnPlatAccountMapper, UnPlatAccount> implements IUnPlatAccountService {
}
