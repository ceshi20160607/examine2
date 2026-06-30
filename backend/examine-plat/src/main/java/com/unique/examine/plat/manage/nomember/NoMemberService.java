package com.unique.examine.plat.manage.nomember;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.PageRequest;
import com.unique.examine.core.api.PageResult;
import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.plat.base.entity.PlatAccount;
import com.unique.examine.plat.base.entity.PlatAccountMemberBinding;
import com.unique.examine.plat.base.entity.PlatNoMemberAccessRequest;
import com.unique.examine.plat.base.entity.PlatRoleMember;
import com.unique.examine.plat.base.entity.PlatSsoBinding;
import com.unique.examine.plat.base.entity.PlatSystem;
import com.unique.examine.plat.base.entity.PlatTenant;
import com.unique.examine.plat.base.service.PlatAccountMemberBindingBaseService;
import com.unique.examine.plat.base.service.PlatNoMemberAccessRequestBaseService;
import com.unique.examine.plat.base.service.PlatRoleMemberBaseService;
import com.unique.examine.plat.base.service.PlatSsoBindingBaseService;
import com.unique.examine.plat.base.service.PlatSystemBaseService;
import com.unique.examine.plat.base.service.PlatTenantBaseService;
import com.unique.examine.plat.manage.common.CurrentAccountProvider;
import com.unique.examine.plat.manage.common.SystemAccessGuard;
import com.unique.examine.plat.manage.common.SystemMemberContextResolver;
import com.unique.examine.plat.manage.common.SystemMemberContextResolver.SystemMemberContext;
import com.unique.examine.plat.manage.nomember.NoMemberModels.NoMemberAccessRequestCreateRequest;
import com.unique.examine.plat.manage.nomember.NoMemberModels.NoMemberAccessRequestQuery;
import com.unique.examine.plat.manage.nomember.NoMemberModels.NoMemberAccessRequestVO;
import com.unique.examine.plat.manage.nomember.NoMemberModels.NoMemberApproveRequest;
import com.unique.examine.plat.manage.nomember.NoMemberModels.NoMemberRejectRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * No-member access request lifecycle service backed by persisted request and binding tables.
 */
@Service
public class NoMemberService {

    private static final int ENABLED = 1;
    private static final int DELETED_NO = 0;
    private static final String STATUS_SUBMITTED = "SUBMITTED";
    private static final String STATUS_REVIEWING = "REVIEWING";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final CurrentAccountProvider currentAccountProvider;
    private final SystemAccessGuard systemAccessGuard;
    private final SystemMemberContextResolver contextResolver;
    private final PlatSystemBaseService systemBaseService;
    private final PlatTenantBaseService tenantBaseService;
    private final PlatNoMemberAccessRequestBaseService requestBaseService;
    private final PlatAccountMemberBindingBaseService accountMemberBindingBaseService;
    private final PlatRoleMemberBaseService roleMemberBaseService;
    private final PlatSsoBindingBaseService ssoBindingBaseService;
    private final ObjectMapper objectMapper;

    public NoMemberService(CurrentAccountProvider currentAccountProvider,
                           SystemAccessGuard systemAccessGuard,
                           SystemMemberContextResolver contextResolver,
                           PlatSystemBaseService systemBaseService,
                           PlatTenantBaseService tenantBaseService,
                           PlatNoMemberAccessRequestBaseService requestBaseService,
                           PlatAccountMemberBindingBaseService accountMemberBindingBaseService,
                           PlatRoleMemberBaseService roleMemberBaseService,
                           PlatSsoBindingBaseService ssoBindingBaseService,
                           ObjectMapper objectMapper) {
        this.currentAccountProvider = currentAccountProvider;
        this.systemAccessGuard = systemAccessGuard;
        this.contextResolver = contextResolver;
        this.systemBaseService = systemBaseService;
        this.tenantBaseService = tenantBaseService;
        this.requestBaseService = requestBaseService;
        this.accountMemberBindingBaseService = accountMemberBindingBaseService;
        this.roleMemberBaseService = roleMemberBaseService;
        this.ssoBindingBaseService = ssoBindingBaseService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a request when an authenticated account has no system member mapping.
     *
     * @param systemId target system id
     * @param request create request
     * @return created request
     */
    @Transactional(rollbackFor = Exception.class)
    public NoMemberAccessRequestVO create(String systemId, NoMemberAccessRequestCreateRequest request) {
        PlatAccount account = currentAccountProvider.currentAccount();
        TargetScope target = resolveTargetScope(systemId, Objects.isNull(request) ? null : request.tenantId());
        requireText(Objects.isNull(request) ? null : request.identityProvider(), "Identity provider is required.");
        String externalUserId = safeText(Objects.isNull(request) ? null : request.externalUserId(),
                String.valueOf(account.getId()));
        PlatNoMemberAccessRequest existing = existingByIdempotency(request.idempotencyKey());
        if (Objects.nonNull(existing)) {
            return toVO(existing, null, null);
        }

        LocalDateTime now = LocalDateTime.now();
        PlatNoMemberAccessRequest entity = new PlatNoMemberAccessRequest();
        entity.setRequestNo(requestNo(request.idempotencyKey()));
        entity.setStatus(STATUS_SUBMITTED);
        entity.setIdentityProvider(request.identityProvider());
        entity.setExternalUserId(externalUserId);
        entity.setTargetSystemId(target.systemId());
        entity.setTenantId(target.tenantId());
        entity.setRequestRole(safeText(request.requestRole(), "SYSTEM_MEMBER"));
        entity.setApproveResult("PENDING");
        entity.setRoleIds(toJson(List.of()));
        entity.setDataScope(toJson(Map.of()));
        entity.setTraceId(RequestContext.current().traceId());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        requestBaseService.saveEntity(entity);
        return toVO(entity, null, null);
    }

    /**
     * Search no-member requests. System admins see the target system queue; non-admin requesters see only their
     * own request rows for the target system.
     *
     * @param systemId target system id
     * @param pageRequest page request
     * @param query query
     * @return request page
     */
    public PageResult<NoMemberAccessRequestVO> search(String systemId, PageRequest pageRequest,
                                                      NoMemberAccessRequestQuery query) {
        PlatAccount account = currentAccountProvider.currentAccount();
        TargetScope target = resolveTargetScope(systemId, Objects.isNull(query) ? null : query.tenantId());
        boolean admin = systemAccessGuard.canManageSystem(account.getId(), systemId);
        int pageNo = pageNo(pageRequest);
        int pageSize = pageSize(pageRequest);
        int offset = (pageNo - 1) * pageSize;
        LambdaQueryWrapper<PlatNoMemberAccessRequest> wrapper = queryWrapper(target, admin, account, query);
        long total = requestBaseService.count(wrapper);
        List<NoMemberAccessRequestVO> records = requestBaseService.list(queryWrapper(target, admin, account, query)
                        .orderByDesc(PlatNoMemberAccessRequest::getUpdatedAt)
                        .last("LIMIT " + offset + "," + pageSize))
                .stream()
                .map(entity -> toVO(entity, null, null))
                .toList();
        return new PageResult<>(records, pageNo, pageSize, total, offset + records.size() < total);
    }

    /**
     * Approve a no-member request only when account, member, role, and data scope are assigned.
     *
     * @param systemId target system id
     * @param requestId request id
     * @param request approve request
     * @return approval result
     */
    @Transactional(rollbackFor = Exception.class)
    public NoMemberAccessRequestVO approve(String systemId, String requestId, NoMemberApproveRequest request) {
        requireSystemAdmin(systemId);
        SystemMemberContext context = contextResolver.resolve(systemId);
        PlatNoMemberAccessRequest entity = requireRequest(context, requestId);
        List<String> roleIds = safeList(Objects.isNull(request) ? null : request.roleIds());
        Map<String, Object> dataScope = safeMap(Objects.isNull(request) ? null : request.dataScope());
        boolean ready = StringUtils.hasText(Objects.isNull(request) ? null : request.accountId())
                && StringUtils.hasText(Objects.isNull(request) ? null : request.systemMemberId())
                && !roleIds.isEmpty() && !dataScope.isEmpty();

        entity.setApproverId(parseOptionalLong(Objects.isNull(request) ? null : request.approverId()));
        entity.setApproveResult(ready ? STATUS_APPROVED : "NEED_MORE_ASSIGNMENT");
        entity.setStatus(ready ? STATUS_APPROVED : STATUS_REVIEWING);
        entity.setRoleIds(toJson(roleIds));
        entity.setDataScope(toJson(dataScope));
        entity.setRejectReason(null);
        entity.setUpdatedAt(LocalDateTime.now());
        requestBaseService.updateById(entity);

        PlatAccountMemberBinding accountBinding = null;
        PlatSsoBinding ssoBinding = null;
        if (ready) {
            accountBinding = upsertAccountMemberBinding(context, request);
            upsertRoleMembers(context, request.systemMemberId(), roleIds);
            ssoBinding = upsertSsoBinding(entity, accountBinding);
        }
        return toVO(entity, accountBinding, Objects.isNull(request) ? null : request.systemMemberId(), ssoBinding);
    }

    /**
     * Reject a no-member request with a business-readable reason.
     *
     * @param systemId target system id
     * @param requestId request id
     * @param request reject request
     * @return rejection result
     */
    @Transactional(rollbackFor = Exception.class)
    public NoMemberAccessRequestVO reject(String systemId, String requestId, NoMemberRejectRequest request) {
        requireSystemAdmin(systemId);
        SystemMemberContext context = contextResolver.resolve(systemId);
        PlatNoMemberAccessRequest entity = requireRequest(context, requestId);
        entity.setStatus(STATUS_REJECTED);
        entity.setApproverId(parseOptionalLong(Objects.isNull(request) ? null : request.approverId()));
        entity.setApproveResult(STATUS_REJECTED);
        entity.setRejectReason(safeText(Objects.isNull(request) ? null : request.rejectReason(),
                "No assignable system member or role."));
        entity.setUpdatedAt(LocalDateTime.now());
        requestBaseService.updateById(entity);
        return toVO(entity, null, null);
    }

    private PlatAccountMemberBinding upsertAccountMemberBinding(SystemMemberContext context,
                                                                NoMemberApproveRequest request) {
        Long accountId = parseRequiredLong(request.accountId(), "Invalid account id.");
        Long systemMemberId = parseRequiredLong(request.systemMemberId(), "Invalid system member id.");
        PlatAccountMemberBinding binding = accountMemberBindingBaseService.getOne(
                new LambdaQueryWrapper<PlatAccountMemberBinding>()
                        .eq(PlatAccountMemberBinding::getAccountId, accountId)
                        .eq(PlatAccountMemberBinding::getSystemId, context.systemId())
                        .eq(PlatAccountMemberBinding::getTenantId, context.tenantId())
                        .eq(PlatAccountMemberBinding::getSystemMemberId, systemMemberId)
                        .last("LIMIT 1"), false);
        if (Objects.isNull(binding)) {
            binding = new PlatAccountMemberBinding();
            binding.setAccountId(accountId);
            binding.setSystemId(context.systemId());
            binding.setTenantId(context.tenantId());
            binding.setSystemMemberId(systemMemberId);
            binding.setCreatedAt(LocalDateTime.now());
        }
        binding.setBindingStatus(ENABLED);
        binding.setUpdatedAt(LocalDateTime.now());
        accountMemberBindingBaseService.saveEntity(binding);
        return binding;
    }

    private void upsertRoleMembers(SystemMemberContext context, String systemMemberId, List<String> roleIds) {
        Long memberId = parseRequiredLong(systemMemberId, "Invalid system member id.");
        for (String roleId : roleIds) {
            Long parsedRoleId = parseRequiredLong(roleId, "Invalid role id.");
            long existing = roleMemberBaseService.count(new LambdaQueryWrapper<PlatRoleMember>()
                    .eq(PlatRoleMember::getSystemId, context.systemId())
                    .eq(PlatRoleMember::getTenantId, context.tenantId())
                    .eq(PlatRoleMember::getSystemMemberId, memberId)
                    .eq(PlatRoleMember::getRoleId, parsedRoleId));
            if (existing > 0) {
                continue;
            }
            PlatRoleMember roleMember = new PlatRoleMember();
            roleMember.setRoleId(parsedRoleId);
            roleMember.setSystemMemberId(memberId);
            roleMember.setSystemId(context.systemId());
            roleMember.setTenantId(context.tenantId());
            roleMember.setCreatedAt(LocalDateTime.now());
            roleMemberBaseService.saveEntity(roleMember);
        }
    }

    private PlatSsoBinding upsertSsoBinding(PlatNoMemberAccessRequest entity,
                                            PlatAccountMemberBinding accountBinding) {
        PlatSsoBinding binding = ssoBindingBaseService.getOne(new LambdaQueryWrapper<PlatSsoBinding>()
                .eq(PlatSsoBinding::getIdentityProvider, entity.getIdentityProvider())
                .eq(PlatSsoBinding::getExternalUserId, entity.getExternalUserId())
                .last("LIMIT 1"), false);
        if (Objects.isNull(binding)) {
            binding = new PlatSsoBinding();
            binding.setIdentityProvider(entity.getIdentityProvider());
            binding.setExternalUserId(entity.getExternalUserId());
            binding.setCreatedAt(LocalDateTime.now());
        }
        binding.setAccountId(accountBinding.getAccountId());
        binding.setSystemId(accountBinding.getSystemId());
        binding.setTenantId(accountBinding.getTenantId());
        binding.setBindingStatus(ENABLED);
        binding.setUpdatedAt(LocalDateTime.now());
        ssoBindingBaseService.saveEntity(binding);
        return binding;
    }

    private PlatNoMemberAccessRequest requireRequest(SystemMemberContext context, String requestId) {
        PlatNoMemberAccessRequest entity = numeric(requestId)
                ? requestBaseService.findById(Long.valueOf(requestId)).orElse(null)
                : requestBaseService.getOne(new LambdaQueryWrapper<PlatNoMemberAccessRequest>()
                .eq(PlatNoMemberAccessRequest::getRequestNo, requestId)
                .last("LIMIT 1"), false);
        if (Objects.isNull(entity) || !Objects.equals(entity.getTargetSystemId(), context.systemId())) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED,
                    "No-member access request does not exist.");
        }
        return entity;
    }

    private PlatNoMemberAccessRequest existingByIdempotency(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return null;
        }
        return requestBaseService.getOne(new LambdaQueryWrapper<PlatNoMemberAccessRequest>()
                .eq(PlatNoMemberAccessRequest::getRequestNo, requestNo(idempotencyKey))
                .last("LIMIT 1"), false);
    }

    private LambdaQueryWrapper<PlatNoMemberAccessRequest> queryWrapper(TargetScope target, boolean admin,
                                                                      PlatAccount account,
                                                                      NoMemberAccessRequestQuery query) {
        LambdaQueryWrapper<PlatNoMemberAccessRequest> wrapper = new LambdaQueryWrapper<PlatNoMemberAccessRequest>()
                .eq(PlatNoMemberAccessRequest::getTargetSystemId, target.systemId());
        if (!admin) {
            wrapper.eq(PlatNoMemberAccessRequest::getExternalUserId, String.valueOf(account.getId()));
        }
        if (StringUtils.hasText(Objects.isNull(query) ? null : query.status())) {
            wrapper.eq(PlatNoMemberAccessRequest::getStatus, query.status());
        }
        if (StringUtils.hasText(Objects.isNull(query) ? null : query.identityProvider())) {
            wrapper.eq(PlatNoMemberAccessRequest::getIdentityProvider, query.identityProvider());
        }
        if (StringUtils.hasText(Objects.isNull(query) ? null : query.externalUserId())) {
            wrapper.eq(PlatNoMemberAccessRequest::getExternalUserId, query.externalUserId());
        }
        if (StringUtils.hasText(Objects.isNull(query) ? null : query.tenantId())) {
            wrapper.eq(PlatNoMemberAccessRequest::getTenantId, parseRequiredLong(query.tenantId(),
                    "Invalid tenant id."));
        }
        if (StringUtils.hasText(Objects.isNull(query) ? null : query.keyword())) {
            wrapper.and(value -> value.like(PlatNoMemberAccessRequest::getExternalUserId, query.keyword())
                    .or().like(PlatNoMemberAccessRequest::getRequestNo, query.keyword()));
        }
        return wrapper;
    }

    private NoMemberAccessRequestVO toVO(PlatNoMemberAccessRequest entity,
                                         PlatAccountMemberBinding accountBinding,
                                         String systemMemberId) {
        PlatSsoBinding ssoBinding = findSsoBinding(entity);
        PlatAccountMemberBinding resolvedBinding = Objects.nonNull(accountBinding)
                ? accountBinding
                : findAccountBinding(ssoBinding);
        String resolvedMemberId = StringUtils.hasText(systemMemberId)
                ? systemMemberId
                : Objects.isNull(resolvedBinding) ? null : String.valueOf(resolvedBinding.getSystemMemberId());
        return toVO(entity, resolvedBinding, resolvedMemberId, ssoBinding);
    }

    private NoMemberAccessRequestVO toVO(PlatNoMemberAccessRequest entity,
                                         PlatAccountMemberBinding accountBinding,
                                         String systemMemberId,
                                         PlatSsoBinding ssoBinding) {
        boolean approved = STATUS_APPROVED.equals(entity.getStatus());
        return new NoMemberAccessRequestVO(String.valueOf(entity.getId()), entity.getStatus(),
                entity.getIdentityProvider(), entity.getExternalUserId(), String.valueOf(entity.getTargetSystemId()),
                String.valueOf(entity.getTenantId()), entity.getRequestRole(),
                Objects.isNull(entity.getApproverId()) ? null : String.valueOf(entity.getApproverId()),
                entity.getApproveResult(), readStringList(entity.getRoleIds()),
                readMap(entity.getDataScope()), entity.getRejectReason(), entity.getTraceId(),
                disabledReason(entity), approved,
                Objects.isNull(accountBinding) ? null : String.valueOf(accountBinding.getId()),
                systemMemberId, Objects.isNull(ssoBinding) ? null : String.valueOf(ssoBinding.getId()),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private PlatSsoBinding findSsoBinding(PlatNoMemberAccessRequest entity) {
        if (!STATUS_APPROVED.equals(entity.getStatus())) {
            return null;
        }
        return ssoBindingBaseService.getOne(new LambdaQueryWrapper<PlatSsoBinding>()
                .eq(PlatSsoBinding::getIdentityProvider, entity.getIdentityProvider())
                .eq(PlatSsoBinding::getExternalUserId, entity.getExternalUserId())
                .eq(PlatSsoBinding::getSystemId, entity.getTargetSystemId())
                .eq(PlatSsoBinding::getTenantId, entity.getTenantId())
                .eq(PlatSsoBinding::getBindingStatus, ENABLED)
                .last("LIMIT 1"), false);
    }

    private PlatAccountMemberBinding findAccountBinding(PlatSsoBinding ssoBinding) {
        if (Objects.isNull(ssoBinding) || Objects.isNull(ssoBinding.getAccountId())) {
            return null;
        }
        return accountMemberBindingBaseService.getOne(new LambdaQueryWrapper<PlatAccountMemberBinding>()
                .eq(PlatAccountMemberBinding::getAccountId, ssoBinding.getAccountId())
                .eq(PlatAccountMemberBinding::getSystemId, ssoBinding.getSystemId())
                .eq(PlatAccountMemberBinding::getTenantId, ssoBinding.getTenantId())
                .eq(PlatAccountMemberBinding::getBindingStatus, ENABLED)
                .last("LIMIT 1"), false);
    }

    private String disabledReason(PlatNoMemberAccessRequest entity) {
        if (STATUS_APPROVED.equals(entity.getStatus())) {
            return null;
        }
        if (STATUS_REJECTED.equals(entity.getStatus())) {
            return "NO_MEMBER_ACCESS_REJECTED";
        }
        if (STATUS_REVIEWING.equals(entity.getStatus())) {
            return "NO_MEMBER_APPROVAL_REQUIRES_MEMBER_ROLE_AND_DATA_SCOPE";
        }
        return "NO_SYSTEM_MEMBER_MAPPING_PENDING_APPROVAL";
    }

    private TargetScope resolveTargetScope(String systemId, String tenantId) {
        PlatSystem system = resolveSystem(systemId);
        PlatTenant tenant = resolveTenant(system, tenantId);
        return new TargetScope(system.getId(), tenant.getId());
    }

    private PlatSystem resolveSystem(String systemId) {
        Long id = parseRequiredLong(systemId, "Invalid system id.");
        PlatSystem system = systemBaseService.getOne(new LambdaQueryWrapper<PlatSystem>()
                .eq(PlatSystem::getId, id)
                .eq(PlatSystem::getDeleted, DELETED_NO)
                .last("LIMIT 1"), false);
        if (Objects.isNull(system)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "System does not exist.");
        }
        return system;
    }

    private PlatTenant resolveTenant(PlatSystem system, String tenantId) {
        LambdaQueryWrapper<PlatTenant> wrapper = new LambdaQueryWrapper<PlatTenant>()
                .eq(PlatTenant::getSystemId, system.getId())
                .eq(PlatTenant::getDeleted, DELETED_NO);
        if (StringUtils.hasText(tenantId)) {
            wrapper.eq(PlatTenant::getId, parseRequiredLong(tenantId, "Invalid tenant id."));
        }
        PlatTenant tenant = tenantBaseService.getOne(wrapper.orderByAsc(PlatTenant::getId)
                .last("LIMIT 1"), false);
        if (Objects.isNull(tenant)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "Tenant does not exist.");
        }
        return tenant;
    }

    private void requireSystemAdmin(String systemId) {
        PlatAccount account = currentAccountProvider.currentAccount();
        if (!systemAccessGuard.canManageSystem(account.getId(), systemId)) {
            throw new BusinessException(CommonErrorCode.PERMISSION_DENIED);
        }
    }

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(CommonErrorCode.OPS_INTERNAL_ERROR,
                    "No-member access request serialization failed.");
        }
    }

    private int pageNo(PageRequest request) {
        return Objects.isNull(request) || request.pageNo() <= 0 ? 1 : request.pageNo();
    }

    private int pageSize(PageRequest request) {
        return Objects.isNull(request) || request.pageSize() <= 0 ? 20 : request.pageSize();
    }

    private Long parseRequiredLong(String value, String message) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, message);
        }
    }

    private Long parseOptionalLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return parseRequiredLong(value, "Invalid id.");
    }

    private List<String> safeList(List<String> values) {
        return Objects.isNull(values) ? List.of() : List.copyOf(values);
    }

    private Map<String, Object> safeMap(Map<String, Object> values) {
        return Objects.isNull(values) ? Map.of() : Map.copyOf(values);
    }

    private String requestNo(String idempotencyKey) {
        return "NMAR-" + safeText(idempotencyKey, RequestContext.current().traceId());
    }

    private boolean numeric(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return value.chars().allMatch(Character::isDigit);
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, message);
        }
    }

    private String safeText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private record TargetScope(Long systemId, Long tenantId) {
    }
}
