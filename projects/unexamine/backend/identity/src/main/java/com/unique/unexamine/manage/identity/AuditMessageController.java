package com.unique.unexamine.manage.identity;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.base.entity.UnPlatAccount;
import com.unique.unexamine.base.entity.UnPlatLogLogin;
import com.unique.unexamine.base.entity.UnPlatLogOpt;
import com.unique.unexamine.base.entity.UnPlatMember;
import com.unique.unexamine.base.entity.UnPlatMessage;
import com.unique.unexamine.base.entity.UnSysLogLogin;
import com.unique.unexamine.base.entity.UnSysLogOpt;
import com.unique.unexamine.base.entity.UnSysMember;
import com.unique.unexamine.base.entity.UnSysMessage;
import com.unique.unexamine.base.entity.UnSysTenant;
import com.unique.unexamine.base.entity.UnSysTenantMember;
import com.unique.unexamine.base.mapper.UnPlatAccountMapper;
import com.unique.unexamine.base.mapper.UnPlatLogLoginMapper;
import com.unique.unexamine.base.mapper.UnPlatLogOptMapper;
import com.unique.unexamine.base.mapper.UnPlatMemberMapper;
import com.unique.unexamine.base.mapper.UnPlatMessageMapper;
import com.unique.unexamine.base.mapper.UnSysLogLoginMapper;
import com.unique.unexamine.base.mapper.UnSysLogOptMapper;
import com.unique.unexamine.base.mapper.UnSysMemberMapper;
import com.unique.unexamine.base.mapper.UnSysMessageMapper;
import com.unique.unexamine.base.mapper.UnSysTenantMapper;
import com.unique.unexamine.base.mapper.UnSysTenantMemberMapper;
import com.unique.unexamine.manage.common.SessionAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** 平台和系统的审计日志查询，以及授权消息发送、当前成员阅读。 */
@RestController
@RequestMapping("/api/common")
public class AuditMessageController {
    private final UnPlatLogLoginMapper platformLogins;
    private final UnPlatLogOptMapper platformOperations;
    private final UnPlatAccountMapper platformAccounts;
    private final UnSysLogLoginMapper systemLogins;
    private final UnSysLogOptMapper systemOperations;
    private final UnPlatMemberMapper platformMembers;
    private final UnPlatMessageMapper platformMessages;
    private final UnSysMessageMapper systemMessages;
    private final UnSysMemberMapper systemMembers;
    private final UnSysTenantMemberMapper tenantMembers;
    private final UnSysTenantMapper tenants;
    private final SessionAccess access;

    public AuditMessageController(UnPlatLogLoginMapper platformLogins, UnPlatLogOptMapper platformOperations,
                                  UnSysLogLoginMapper systemLogins, UnSysLogOptMapper systemOperations,
                                  UnPlatAccountMapper platformAccounts, UnPlatMemberMapper platformMembers,
                                  UnPlatMessageMapper platformMessages, UnSysMessageMapper systemMessages,
                                  UnSysMemberMapper systemMembers, UnSysTenantMemberMapper tenantMembers,
                                  UnSysTenantMapper tenants,
                                  SessionAccess access) {
        this.platformLogins = platformLogins;
        this.platformOperations = platformOperations;
        this.systemLogins = systemLogins;
        this.systemOperations = systemOperations;
        this.platformAccounts = platformAccounts;
        this.platformMembers = platformMembers;
        this.platformMessages = platformMessages;
        this.systemMessages = systemMessages;
        this.systemMembers = systemMembers;
        this.tenantMembers = tenantMembers;
        this.tenants = tenants;
        this.access = access;
    }

    /** 平台管理员按账号和结果筛选登录尝试。 */
    @GetMapping("/logs/platform-login")
    public IPage<UnPlatLogLogin> platformLoginLogs(@RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "20") long size,
                                                   @RequestParam(defaultValue = "loginTime") String sort,
                                                   @RequestParam(required = false) String account,
                                                   @RequestParam(required = false) Byte success,
                                                   HttpServletRequest request) {
        access.platformAdmin(request);
        LambdaQueryWrapper<UnPlatLogLogin> query = new LambdaQueryWrapper<>();
        query.eq(account != null && !account.isBlank(), UnPlatLogLogin::getLoginAccount, account);
        query.eq(success != null, UnPlatLogLogin::getSuccess, success);
        switch (sort) {
            case "loginTime" -> query.orderByDesc(UnPlatLogLogin::getLoginTime);
            case "loginAccount" -> query.orderByAsc(UnPlatLogLogin::getLoginAccount);
            default -> throw bad("不支持的登录日志排序字段");
        }
        return platformLogins.selectPage(paging(page, size), query);
    }

    /** 平台操作日志只供平台管理员查询。 */
    @GetMapping("/logs/platform-operation")
    public IPage<UnPlatLogOpt> platformOperationLogs(@RequestParam(defaultValue = "1") long page,
                                                     @RequestParam(defaultValue = "20") long size,
                                                     @RequestParam(defaultValue = "createTime") String sort,
                                                     @RequestParam(required = false) Long accountId,
                                                     @RequestParam(required = false) String actionCode,
                                                     HttpServletRequest request) {
        access.platformAdmin(request);
        LambdaQueryWrapper<UnPlatLogOpt> query = new LambdaQueryWrapper<>();
        query.eq(accountId != null, UnPlatLogOpt::getAccountId, accountId);
        query.eq(actionCode != null && !actionCode.isBlank(), UnPlatLogOpt::getActionCode, actionCode);
        switch (sort) {
            case "createTime" -> query.orderByDesc(UnPlatLogOpt::getCreateTime);
            case "actionCode" -> query.orderByAsc(UnPlatLogOpt::getActionCode);
            default -> throw bad("不支持的操作日志排序字段");
        }
        return platformOperations.selectPage(paging(page, size), query);
    }

    /** 系统登录日志要求平台管理员明确指定系统。 */
    @GetMapping("/logs/system-login")
    public IPage<UnSysLogLogin> systemLoginLogs(@RequestParam long systemId,
                                               @RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "20") long size,
                                               @RequestParam(defaultValue = "loginTime") String sort,
                                               @RequestParam(required = false) String account,
                                               HttpServletRequest request) {
        access.platformAdmin(request);
        LambdaQueryWrapper<UnSysLogLogin> query = new LambdaQueryWrapper<UnSysLogLogin>()
                .eq(UnSysLogLogin::getSystemId, systemId)
                .eq(account != null && !account.isBlank(), UnSysLogLogin::getLoginAccount, account);
        switch (sort) {
            case "loginTime" -> query.orderByDesc(UnSysLogLogin::getLoginTime);
            case "loginAccount" -> query.orderByAsc(UnSysLogLogin::getLoginAccount);
            default -> throw bad("不支持的系统登录日志排序字段");
        }
        return systemLogins.selectPage(paging(page, size), query);
    }

    /** 系统操作日志可限定租户，租户必须属于所选系统。 */
    @GetMapping("/logs/system-operation")
    public IPage<UnSysLogOpt> systemOperationLogs(@RequestParam long systemId,
                                                 @RequestParam(required = false) Long tenantId,
                                                 @RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "20") long size,
                                                 @RequestParam(defaultValue = "createTime") String sort,
                                                 HttpServletRequest request) {
        access.platformAdmin(request);
        if (tenantId != null) {
            UnSysTenant tenant = tenants.selectById(tenantId);
            if (tenant == null || !tenant.getSystemId().equals(systemId)) throw bad("租户不属于该系统");
        }
        LambdaQueryWrapper<UnSysLogOpt> query = new LambdaQueryWrapper<UnSysLogOpt>()
                .eq(UnSysLogOpt::getSystemId, systemId)
                .eq(tenantId != null, UnSysLogOpt::getTenantId, tenantId);
        switch (sort) {
            case "createTime" -> query.orderByDesc(UnSysLogOpt::getCreateTime);
            case "actionCode" -> query.orderByAsc(UnSysLogOpt::getActionCode);
            default -> throw bad("不支持的系统操作日志排序字段");
        }
        return systemOperations.selectPage(paging(page, size), query);
    }

    /** 平台消息只返回当前登录账号关联成员收到的内容。 */
    @GetMapping("/messages/platform")
    public IPage<UnPlatMessage> platformMessages(@RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "20") long size,
                                                 @RequestParam(defaultValue = "createTime") String sort,
                                                 @RequestParam(required = false) Boolean unread,
                                                 HttpServletRequest request) {
        Long memberId = platformMember(request).getId();
        LambdaQueryWrapper<UnPlatMessage> query = new LambdaQueryWrapper<UnPlatMessage>()
                .eq(UnPlatMessage::getRecipientMemberId, memberId);
        if (unread != null) {
            if (unread) query.isNull(UnPlatMessage::getReadTime);
            else query.isNotNull(UnPlatMessage::getReadTime);
        }
        switch (sort) {
            case "createTime" -> query.orderByDesc(UnPlatMessage::getCreateTime);
            case "title" -> query.orderByAsc(UnPlatMessage::getTitle);
            default -> throw bad("不支持的平台消息排序字段");
        }
        return platformMessages.selectPage(paging(page, size), query);
    }

    /** 平台管理员只能向有效且可登录的平台成员发送消息。 */
    @PostMapping("/messages/platform")
    @ResponseStatus(HttpStatus.CREATED)
    public UnPlatMessage sendPlatformMessage(@Valid @RequestBody PlatformMessageInput input,
                                              HttpServletRequest request) {
        access.platformAdmin(request);
        checkRelated(input.relatedType(), input.relatedId());
        UnPlatMember recipient = platformMembers.selectById(input.recipientMemberId());
        if (recipient == null || recipient.getStatus() == null || recipient.getStatus() != 1
                || recipient.getAccountId() == null) throw bad("接收平台成员无效或未开通账号");
        UnPlatAccount account = platformAccounts.selectById(recipient.getAccountId());
        if (account == null || account.getStatus() == null || account.getStatus() != 1)
            throw bad("接收平台成员的账号已停用");
        UnPlatMessage item = new UnPlatMessage();
        item.setRecipientMemberId(recipient.getId());
        item.setTitle(input.title().trim());
        item.setContent(input.content());
        item.setMessageType(input.messageType().trim());
        item.setRelatedType(input.relatedType());
        item.setRelatedId(input.relatedId());
        platformMessages.insert(item);
        return item;
    }

    /** 标记已读前校验消息接收者是当前平台成员。 */
    @PutMapping("/messages/platform/{id}/read")
    public UnPlatMessage readPlatformMessage(@PathVariable long id, HttpServletRequest request) {
        Long memberId = platformMember(request).getId();
        UnPlatMessage item = platformMessages.selectById(id);
        if (item == null || !item.getRecipientMemberId().equals(memberId)) throw missing("平台消息不存在");
        if (item.getReadTime() == null) {
            // 与数据库 DATETIME(3) 一致，首次响应和重新读取的已读时间保持相同。
            item.setReadTime(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
            platformMessages.updateById(item);
        }
        return item;
    }

    /** 系统消息默认只取系统级消息；租户消息须明确选择有权限的租户。 */
    @GetMapping("/messages/system")
    public IPage<UnSysMessage> systemMessages(@RequestParam(required = false) Long tenantId,
                                               @RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "20") long size,
                                               @RequestParam(defaultValue = "createTime") String sort,
                                               @RequestParam(required = false) Boolean unread,
                                               HttpServletRequest request) {
        UnSysMember member = access.systemMember(request);
        if (tenantId != null) access.tenantMember(request, tenantId);
        LambdaQueryWrapper<UnSysMessage> query = new LambdaQueryWrapper<UnSysMessage>()
                .eq(UnSysMessage::getSystemId, member.getSystemId())
                .eq(UnSysMessage::getRecipientMemberId, member.getId());
        if (tenantId == null) query.isNull(UnSysMessage::getTenantId);
        else query.eq(UnSysMessage::getTenantId, tenantId);
        if (unread != null) {
            if (unread) query.isNull(UnSysMessage::getReadTime);
            else query.isNotNull(UnSysMessage::getReadTime);
        }
        switch (sort) {
            case "createTime" -> query.orderByDesc(UnSysMessage::getCreateTime);
            case "title" -> query.orderByAsc(UnSysMessage::getTitle);
            default -> throw bad("不支持的系统消息排序字段");
        }
        return systemMessages.selectPage(paging(page, size), query);
    }

    /** 租户管理员按租户成员主键选接收人，服务端转换为该租户有效系统成员。 */
    @PostMapping("/messages/system")
    @ResponseStatus(HttpStatus.CREATED)
    public UnSysMessage sendTenantMessage(@Valid @RequestBody TenantMessageInput input,
                                           HttpServletRequest request) {
        access.tenantAdmin(request, input.tenantId());
        checkRelated(input.relatedType(), input.relatedId());
        UnSysTenant tenant = tenants.selectById(input.tenantId());
        UnSysTenantMember recipient = tenantMembers.selectById(input.recipientTenantMemberId());
        if (tenant == null || recipient == null || !recipient.getTenantId().equals(tenant.getId())
                || recipient.getStatus() == null || recipient.getStatus() != 1)
            throw bad("接收人不是该租户的有效成员");
        UnSysMember member = systemMembers.selectById(recipient.getMemberId());
        if (member == null || !member.getSystemId().equals(tenant.getSystemId())
                || member.getStatus() == null || member.getStatus() != 1)
            throw bad("接收成员的系统身份无效");
        UnSysMessage item = new UnSysMessage();
        item.setSystemId(tenant.getSystemId());
        item.setTenantId(tenant.getId());
        item.setRecipientMemberId(member.getId());
        item.setTitle(input.title().trim());
        item.setContent(input.content());
        item.setMessageType(input.messageType().trim());
        item.setRelatedType(input.relatedType());
        item.setRelatedId(input.relatedId());
        systemMessages.insert(item);
        return item;
    }

    /** 标记系统消息已读时再次校验所属系统、成员及租户。 */
    @PutMapping("/messages/system/{id}/read")
    public UnSysMessage readSystemMessage(@PathVariable long id, HttpServletRequest request) {
        UnSysMember member = access.systemMember(request);
        UnSysMessage item = systemMessages.selectById(id);
        if (item == null || !item.getSystemId().equals(member.getSystemId())
                || !item.getRecipientMemberId().equals(member.getId())) throw missing("系统消息不存在");
        if (item.getTenantId() != null) access.tenantMember(request, item.getTenantId());
        if (item.getReadTime() == null) {
            // 与数据库 DATETIME(3) 一致，首次响应和重新读取的已读时间保持相同。
            item.setReadTime(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
            systemMessages.updateById(item);
        }
        return item;
    }

    private UnPlatMember platformMember(HttpServletRequest request) {
        UnPlatAccount account = access.platformAccount(request);
        UnPlatMember member = platformMembers.selectOne(new LambdaQueryWrapper<UnPlatMember>()
                .eq(UnPlatMember::getAccountId, account.getId()).eq(UnPlatMember::getStatus, 1));
        if (member == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号没有平台成员资料");
        return member;
    }

    private <T> Page<T> paging(long page, long size) {
        if (page < 1 || size < 1 || size > 100) throw bad("分页参数无效");
        return new Page<>(page, size);
    }

    private ResponseStatusException bad(String text) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, text); }
    private ResponseStatusException missing(String text) { return new ResponseStatusException(HttpStatus.NOT_FOUND, text); }

    /** 关联业务对象的类型和主键必须同时提供，避免生成无法打开的消息入口。 */
    private void checkRelated(String relatedType, Long relatedId) {
        if ((relatedType == null) != (relatedId == null) || relatedId != null && relatedId <= 0)
            throw bad("消息关联对象参数不完整");
    }

    /** 平台消息只接受接收成员和正文，不接受客户端指定已读时间。 */
    public record PlatformMessageInput(@Positive long recipientMemberId,
                                       @NotBlank @Size(max = 200) String title,
                                       @NotBlank @Size(max = 10000) String content,
                                       @NotBlank @Size(max = 50) String messageType,
                                       @Size(max = 64) String relatedType, Long relatedId) {}

    /** 租户消息由租户成员关系确定系统身份，不能由客户端指定系统或接收账号。 */
    public record TenantMessageInput(@Positive long tenantId, @Positive long recipientTenantMemberId,
                                     @NotBlank @Size(max = 200) String title,
                                     @NotBlank @Size(max = 10000) String content,
                                     @NotBlank @Size(max = 50) String messageType,
                                     @Size(max = 64) String relatedType, Long relatedId) {}
}
