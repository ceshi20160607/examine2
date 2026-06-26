package com.unique.examine.plat.manage.common;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.plat.base.entity.PlatAccount;
import com.unique.examine.plat.base.service.PlatAccountBaseService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Resolves the current platform account for real-system smoke before token/session middleware is completed.
 */
@Component
public class CurrentAccountProvider {

    public static final String ACCOUNT_ID_HEADER = "X-Account-Id";
    private static final int ENABLED = 1;
    private static final int DELETED_NO = 0;

    private final PlatAccountBaseService accountBaseService;
    private final boolean allowAccountIdHeader;

    public CurrentAccountProvider(PlatAccountBaseService accountBaseService,
                                  @Value("${unexamine.security.allow-account-id-header:false}")
                                  boolean allowAccountIdHeader) {
        this.accountBaseService = accountBaseService;
        this.allowAccountIdHeader = allowAccountIdHeader;
    }

    /**
     * Resolve the current account from the authenticated request context.
     *
     * @return current account
     */
    public PlatAccount currentAccount() {
        Long accountId = RequestContext.current().accountId();
        if (Objects.isNull(accountId) && allowAccountIdHeader) {
            accountId = headerAccountId();
        }
        if (Objects.isNull(accountId)) {
            throw new BusinessException(CommonErrorCode.AUTH_UNAUTHORIZED);
        }
        PlatAccount account = accountBaseService.getOne(new LambdaQueryWrapper<PlatAccount>()
                .eq(PlatAccount::getId, accountId)
                .eq(PlatAccount::getStatus, ENABLED)
                .eq(PlatAccount::getDeleted, DELETED_NO)
                .last("LIMIT 1"), false);
        if (Objects.isNull(account)) {
            throw new BusinessException(CommonErrorCode.AUTH_UNAUTHORIZED);
        }
        return account;
    }

    private Long headerAccountId() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (Objects.isNull(attributes)) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        String value = request.getHeader(ACCOUNT_ID_HEADER);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
