package com.unique.unexamine.sys.manage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.unique.unexamine.sys.base.entity.UnSysLogLogin;
import com.unique.unexamine.sys.base.entity.UnSysLogOpt;
import com.unique.unexamine.sys.base.entity.UnSysMember;
import com.unique.unexamine.sys.base.entity.UnSysMessage;
import com.unique.unexamine.sys.base.entity.UnSysTenant;
import com.unique.unexamine.sys.base.entity.UnSysTenantMember;
import com.unique.unexamine.sys.base.service.IUnSysLogLoginService;
import com.unique.unexamine.sys.base.service.IUnSysLogOptService;
import com.unique.unexamine.sys.base.service.IUnSysMemberService;
import com.unique.unexamine.sys.base.service.IUnSysMessageService;
import com.unique.unexamine.sys.base.service.IUnSysTenantService;
import com.unique.unexamine.sys.base.service.IUnSysTenantMemberService;
import com.unique.unexamine.plat.manage.PlatformSessionAccess;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

/** 系统与租户审计日志及消息入口。 */
@Tag(name = "系统审计与消息", description = "系统日志查询和系统消息。")
@RestController
@RequestMapping("/api/common")
public class SystemAuditMessageController {
    private final IUnSysLogLoginService systemLogins;
    private final IUnSysLogOptService systemOperations;
    private final IUnSysMessageService systemMessages;
    private final IUnSysMemberService systemMembers;
    private final IUnSysTenantMemberService tenantMembers;
    private final IUnSysTenantService tenants;
    private final PlatformSessionAccess platformAccess;
    private final SystemSessionAccess access;

    public SystemAuditMessageController(IUnSysLogLoginService systemLogins, IUnSysLogOptService systemOperations,
                                        IUnSysMessageService systemMessages, IUnSysMemberService systemMembers,
                                        IUnSysTenantMemberService tenantMembers, IUnSysTenantService tenants,
                                        PlatformSessionAccess platformAccess, SystemSessionAccess access) {
        this.systemLogins = systemLogins;
        this.systemOperations = systemOperations;
        this.systemMessages = systemMessages;
        this.systemMembers = systemMembers;
        this.tenantMembers = tenantMembers;
        this.tenants = tenants;
        this.platformAccess = platformAccess;
        this.access = access;
    }

    /** 系统登录日志要求平台管理员明确指定系统。 */
    @Operation(summary = "查询系统登录日志（系统审计与消息）")
    @GetMapping("/logs/system-login")
    public IPage<UnSysLogLogin> systemLoginLogs(@RequestParam long systemId,
                                               @RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "20") long size,
                                               @RequestParam(defaultValue = "loginTime") String sort,
                                               @RequestParam(required = false) String account,
                                               HttpServletRequest request) {
        platformAccess.platformAdmin(request);
        LambdaQueryWrapper<UnSysLogLogin> query = new LambdaQueryWrapper<UnSysLogLogin>()
                .eq(UnSysLogLogin::getSystemId, systemId)
                .eq(account != null && !account.isBlank(), UnSysLogLogin::getLoginAccount, account);
        switch (sort) {
            case "loginTime" -> query.orderByDesc(UnSysLogLogin::getLoginTime);
            case "loginAccount" -> query.orderByAsc(UnSysLogLogin::getLoginAccount);
            default -> throw bad("不支持的系统登录日志排序字段");
        }
        return systemLogins.page(paging(page, size), query);
    }

    /** 系统操作日志可限定租户，租户必须属于所选系统。 */
    @Operation(summary = "查询系统操作日志（系统审计与消息）")
    @GetMapping("/logs/system-operation")
    public IPage<UnSysLogOpt> systemOperationLogs(@RequestParam long systemId,
                                                 @RequestParam(required = false) Long tenantId,
                                                 @RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "20") long size,
                                                 @RequestParam(defaultValue = "createTime") String sort,
                                                 HttpServletRequest request) {
        platformAccess.platformAdmin(request);
        if (tenantId != null) {
            UnSysTenant tenant = tenants.getById(tenantId);
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
        return systemOperations.page(paging(page, size), query);
    }

    /** 系统消息默认只取系统级消息；租户消息须明确选择有权限的租户。 */
    @Operation(summary = "查询系统消息（系统审计与消息）")
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
        return systemMessages.page(paging(page, size), query);
    }

    /** 租户管理员按租户成员主键选接收人，服务端转换为该租户有效系统成员。 */
    @Operation(summary = "发送系统消息（系统审计与消息）")
    @PostMapping("/messages/system")
    @ResponseStatus(HttpStatus.CREATED)
    public UnSysMessage sendTenantMessage(@Valid @RequestBody TenantMessageInput input,
                                           HttpServletRequest request) {
        access.tenantAdmin(request, input.tenantId());
        checkRelated(input.relatedType(), input.relatedId());
        UnSysTenant tenant = tenants.getById(input.tenantId());
        UnSysTenantMember recipient = tenantMembers.getById(input.recipientTenantMemberId());
        if (tenant == null || recipient == null || !recipient.getTenantId().equals(tenant.getId())
                || recipient.getStatus() == null || recipient.getStatus() != 1)
            throw bad("接收人不是该租户的有效成员");
        UnSysMember member = systemMembers.getById(recipient.getMemberId());
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
        systemMessages.save(item);
        return item;
    }

    /** 标记系统消息已读时再次校验所属系统、成员及租户。 */
    @Operation(summary = "标记系统消息已读（系统审计与消息）")
    @PutMapping("/messages/system/{id}/read")
    public UnSysMessage readSystemMessage(@PathVariable long id, HttpServletRequest request) {
        UnSysMember member = access.systemMember(request);
        UnSysMessage item = systemMessages.getById(id);
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


    /** 租户消息由租户成员关系确定系统身份，不能由客户端指定系统或接收账号。 */
    public record TenantMessageInput(@Positive long tenantId, @Positive long recipientTenantMemberId,
                                     @NotBlank @Size(max = 200) String title,
                                     @NotBlank @Size(max = 10000) String content,
                                     @NotBlank @Size(max = 50) String messageType,
                                     @Size(max = 64) String relatedType, Long relatedId) {}
}
