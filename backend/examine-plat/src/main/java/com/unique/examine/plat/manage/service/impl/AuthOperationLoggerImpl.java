package com.unique.examine.plat.manage.service.impl;

import java.time.LocalDateTime;

import com.unique.examine.core.base.entity.OperationLog;
import com.unique.examine.core.base.service.IOperationLogService;
import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.context.RequestContextHolder;
import com.unique.examine.plat.base.entity.Account;
import com.unique.examine.plat.manage.service.AuthOperationLogger;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 认证操作审计日志实现。
 */
@Service
@RequiredArgsConstructor
public class AuthOperationLoggerImpl implements AuthOperationLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthOperationLoggerImpl.class);

    private final IOperationLogService operationLogService;

    /**
     * 记录认证操作。
     *
     * @param account 平台账号，可为空
     * @param action 操作动作
     * @param success 是否成功
     * @param errorCode 失败错误码
     * @param summary 审计摘要
     */
    @Override
    public void log(Account account, String action, boolean success, String errorCode, String summary) {
        try {
            RequestContext context = RequestContextHolder.get();
            OperationLog log = new OperationLog()
                    .setRequestId(context == null ? null : context.getRequestId())
                    .setTraceId(context == null ? null : context.getTraceId())
                    .setOperatorType("ACCOUNT")
                    .setOperatorId(account == null || account.getId() == null ? null : String.valueOf(account.getId()))
                    .setOperatorName(account == null ? null : account.getDisplayName())
                    .setModule("AUTH")
                    .setBizType("AUTH_SESSION")
                    .setBizId(account == null || account.getId() == null ? null : String.valueOf(account.getId()))
                    .setAction(action)
                    .setResult(success ? "SUCCESS" : "FAILED")
                    .setErrorCode(errorCode)
                    .setSummary(summary)
                    .setCreatedAt(LocalDateTime.now())
                    .setUpdatedAt(LocalDateTime.now());
            operationLogService.save(log);
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to write auth operation log", exception);
        }
    }
}
