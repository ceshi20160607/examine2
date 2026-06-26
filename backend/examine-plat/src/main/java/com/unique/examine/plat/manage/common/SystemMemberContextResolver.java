package com.unique.examine.plat.manage.common;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.plat.base.entity.PlatAccount;
import com.unique.examine.plat.base.entity.PlatAccountMemberBinding;
import com.unique.examine.plat.base.service.PlatAccountMemberBindingBaseService;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Resolves current account-member binding for system backend APIs.
 */
@Component
public class SystemMemberContextResolver {

    private static final int ENABLED = 1;

    private final CurrentAccountProvider currentAccountProvider;
    private final PlatAccountMemberBindingBaseService bindingBaseService;

    public SystemMemberContextResolver(CurrentAccountProvider currentAccountProvider,
                                       PlatAccountMemberBindingBaseService bindingBaseService) {
        this.currentAccountProvider = currentAccountProvider;
        this.bindingBaseService = bindingBaseService;
    }

    /**
     * Resolve current system context from route system id and current account header.
     *
     * @param systemId route system id
     * @return resolved context
     */
    public SystemMemberContext resolve(String systemId) {
        Long resolvedSystemId = parseRequiredId(systemId, "系统ID格式不正确");
        PlatAccount account = currentAccountProvider.currentAccount();
        PlatAccountMemberBinding binding = bindingBaseService.getOne(
                new LambdaQueryWrapper<PlatAccountMemberBinding>()
                        .eq(PlatAccountMemberBinding::getAccountId, account.getId())
                        .eq(PlatAccountMemberBinding::getSystemId, resolvedSystemId)
                        .eq(PlatAccountMemberBinding::getBindingStatus, ENABLED)
                        .orderByAsc(PlatAccountMemberBinding::getId)
                        .last("LIMIT 1"), false);
        if (Objects.isNull(binding)) {
            throw new BusinessException(CommonErrorCode.PERMISSION_DENIED, "账号没有目标系统的成员映射");
        }
        return new SystemMemberContext(account.getId(), binding.getSystemId(), binding.getTenantId(),
                binding.getSystemMemberId());
    }

    /**
     * Parse route ids.
     *
     * @param value raw id
     * @param message invalid message
     * @return parsed id
     */
    public Long parseRequiredId(String value, String message) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, message);
        }
    }

    /**
     * Current system member context.
     *
     * @param accountId account id
     * @param systemId system id
     * @param tenantId tenant id
     * @param systemMemberId system member id
     */
    public record SystemMemberContext(Long accountId, Long systemId, Long tenantId, Long systemMemberId) {
    }
}
