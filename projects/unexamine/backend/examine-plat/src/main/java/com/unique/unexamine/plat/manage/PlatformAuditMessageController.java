package com.unique.unexamine.plat.manage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.unique.unexamine.plat.base.entity.UnPlatAccount;
import com.unique.unexamine.plat.base.entity.UnPlatLogLogin;
import com.unique.unexamine.plat.base.entity.UnPlatLogOpt;
import com.unique.unexamine.plat.base.entity.UnPlatMember;
import com.unique.unexamine.plat.base.entity.UnPlatMessage;
import com.unique.unexamine.plat.base.mapper.UnPlatAccountMapper;
import com.unique.unexamine.plat.base.mapper.UnPlatLogLoginMapper;
import com.unique.unexamine.plat.base.mapper.UnPlatLogOptMapper;
import com.unique.unexamine.plat.base.mapper.UnPlatMemberMapper;
import com.unique.unexamine.plat.base.mapper.UnPlatMessageMapper;
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

/** 平台审计日志及消息入口。 */
@Tag(name = "平台审计与消息", description = "平台日志查询和平台消息。")
@RestController
@RequestMapping("/api/common")
public class PlatformAuditMessageController {
    private final UnPlatLogLoginMapper platformLogins;
    private final UnPlatLogOptMapper platformOperations;
    private final UnPlatAccountMapper platformAccounts;
    private final UnPlatMemberMapper platformMembers;
    private final UnPlatMessageMapper platformMessages;
    private final PlatformSessionAccess access;

    public PlatformAuditMessageController(UnPlatLogLoginMapper platformLogins, UnPlatLogOptMapper platformOperations,
                                          UnPlatAccountMapper platformAccounts, UnPlatMemberMapper platformMembers,
                                          UnPlatMessageMapper platformMessages, PlatformSessionAccess access) {
        this.platformLogins = platformLogins;
        this.platformOperations = platformOperations;
        this.platformAccounts = platformAccounts;
        this.platformMembers = platformMembers;
        this.platformMessages = platformMessages;
        this.access = access;
    }

    /** 平台管理员按账号和结果筛选登录尝试。 */
    @Operation(summary = "查询平台登录日志（平台审计与消息）")
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
    @Operation(summary = "查询平台操作日志（平台审计与消息）")
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

    /** 平台消息只返回当前登录账号关联成员收到的内容。 */
    @Operation(summary = "查询平台消息（平台审计与消息）")
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
    @Operation(summary = "发送平台消息（平台审计与消息）")
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
    @Operation(summary = "标记平台消息已读（平台审计与消息）")
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

}
