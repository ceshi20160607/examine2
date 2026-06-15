package com.unique.examine.plat.manage.service;

import com.unique.examine.plat.base.entity.Account;

/**
 * 认证操作审计日志边界。
 */
public interface AuthOperationLogger {

    /**
     * 记录认证操作。
     *
     * @param account 平台账号，可为空
     * @param action 操作动作
     * @param success 是否成功
     * @param errorCode 失败错误码
     * @param summary 审计摘要
     */
    void log(Account account, String action, boolean success, String errorCode, String summary);
}
