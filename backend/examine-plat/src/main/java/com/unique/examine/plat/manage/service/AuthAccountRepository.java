package com.unique.examine.plat.manage.service;

import java.util.Optional;

import com.unique.examine.plat.base.entity.Account;

/**
 * 认证场景使用的平台账号仓储边界。
 */
public interface AuthAccountRepository {

    /**
     * 按登录名查询未删除平台账号。
     *
     * @param loginName 登录名
     * @return 平台账号
     */
    Optional<Account> findActiveByLoginName(String loginName);

    /**
     * 按 ID 查询未删除平台账号。
     *
     * @param accountId 平台账号 ID
     * @return 平台账号
     */
    Optional<Account> findActiveById(Long accountId);

    /**
     * 保存平台账号。
     *
     * @param account 平台账号
     */
    void save(Account account);

    /**
     * 按主键更新平台账号。
     *
     * @param account 平台账号
     */
    void updateById(Account account);
}
