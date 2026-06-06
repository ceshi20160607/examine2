package com.unique.examine.plat.base.service.impl;

import com.unique.examine.plat.base.entity.Account;
import com.unique.examine.plat.base.mapper.AccountMapper;
import com.unique.examine.plat.base.service.IAccountService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 平台账号 服务实现类
 * </p>
 *
 * @author codex
 * @since 2026-06-05
 */
@Service
public class AccountServiceImpl extends ServiceImpl<AccountMapper, Account> implements IAccountService {

}
