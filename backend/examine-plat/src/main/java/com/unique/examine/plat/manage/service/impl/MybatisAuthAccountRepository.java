package com.unique.examine.plat.manage.service.impl;

import java.util.Optional;

import com.unique.examine.plat.base.entity.Account;
import com.unique.examine.plat.base.service.IAccountService;
import com.unique.examine.plat.manage.service.AuthAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 基于 MyBatis-Plus 的平台账号认证仓储实现。
 */
@Service
@RequiredArgsConstructor
public class MybatisAuthAccountRepository implements AuthAccountRepository {

    private static final long ACTIVE_DELETE_TOKEN = 0L;

    private final IAccountService accountService;

    /**
     * 按登录名查询未删除平台账号。
     *
     * @param loginName 登录名
     * @return 平台账号
     */
    @Override
    public Optional<Account> findActiveByLoginName(String loginName) {
        return Optional.ofNullable(accountService.lambdaQuery()
                .eq(Account::getLoginName, loginName)
                .eq(Account::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .one());
    }

    /**
     * 按 ID 查询未删除平台账号。
     *
     * @param accountId 平台账号 ID
     * @return 平台账号
     */
    @Override
    public Optional<Account> findActiveById(Long accountId) {
        return Optional.ofNullable(accountService.lambdaQuery()
                .eq(Account::getId, accountId)
                .eq(Account::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .one());
    }

    /**
     * 保存平台账号。
     *
     * @param account 平台账号
     */
    @Override
    public void save(Account account) {
        accountService.save(account);
    }

    /**
     * 按主键更新平台账号。
     *
     * @param account 平台账号
     */
    @Override
    public void updateById(Account account) {
        accountService.updateById(account);
    }
}
