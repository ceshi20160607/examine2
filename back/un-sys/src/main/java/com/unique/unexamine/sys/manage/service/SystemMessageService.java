package com.unique.unexamine.sys.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.sys.base.entity.UnSysMessage;
import com.unique.unexamine.sys.base.service.IUnSysMessageService;
import com.unique.unexamine.sys.manage.dto.SystemManageDto.SystemContext;
import com.unique.unexamine.sys.manage.dto.SystemMessageDto.MessageSearch;
import com.unique.unexamine.sys.manage.dto.SystemMessageDto.MessageView;
import com.unique.unexamine.sys.manage.dto.SystemMessageDto.UnreadSummary;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 系统成员只管理当前系统租户的收件箱，发送由模板服务执行。 */
@Service
public class SystemMessageService {
    private final SystemManageService systems;
    private final IUnSysMessageService messages;

    public SystemMessageService(SystemManageService systems, IUnSysMessageService messages) {
        this.systems = systems; this.messages = messages;
    }

    /** 本人的当前租户消息按状态与关键字分页。 */
    public Page<MessageView> mine(String token, Long systemId, Long tenantId, MessageSearch search) {
        SystemContext context = systems.context(token, systemId, tenantId);
        MessageSearch criteria = search == null ? new MessageSearch() : search;
        var where = new LambdaQueryWrapper<UnSysMessage>()
                .eq(UnSysMessage::getSystemId, context.systemId())
                .eq(UnSysMessage::getTenantId, context.tenantId())
                .eq(UnSysMessage::getRecipientMemberId, context.memberId())
                .orderByDesc(UnSysMessage::getId);
        if (criteria.getStatus() != null && !criteria.getStatus().isBlank())
            where.eq(UnSysMessage::getStatus, status(criteria.getStatus()));
        if (criteria.getKeyword() != null && !criteria.getKeyword().isBlank()) {
            if (criteria.getKeyword().length() > 100)
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "消息搜索内容过长");
            String keyword = criteria.getKeyword().trim();
            where.and(w -> w.like(UnSysMessage::getTitle, keyword).or().like(UnSysMessage::getContent, keyword));
        }
        Page<UnSysMessage> page = messages.page(criteria.parse(), where);
        return new Page<MessageView>(page.getCurrent(), page.getSize(), page.getTotal())
                .setRecords(page.getRecords().stream().map(this::view).toList());
    }

    /** 顶部消息角标只统计当前成员在当前租户未读的消息。 */
    public UnreadSummary unread(String token, Long systemId, Long tenantId) {
        return unread(systems.context(token, systemId, tenantId));
    }

    /** 批量已读只更新当前成员的未读行，重复执行保持幂等。 */
    @Transactional(rollbackFor = Exception.class)
    public UnreadSummary readAll(String token, Long systemId, Long tenantId) {
        SystemContext context = systems.context(token, systemId, tenantId);
        messages.update(new LambdaUpdateWrapper<UnSysMessage>()
                .eq(UnSysMessage::getSystemId, context.systemId())
                .eq(UnSysMessage::getTenantId, context.tenantId())
                .eq(UnSysMessage::getRecipientMemberId, context.memberId())
                .eq(UnSysMessage::getStatus, "UNREAD")
                .set(UnSysMessage::getStatus, "READ")
                .set(UnSysMessage::getReadTime, LocalDateTime.now()));
        return unread(context);
    }

    private UnreadSummary unread(SystemContext context) {
        return new UnreadSummary(messages.count(new LambdaQueryWrapper<UnSysMessage>()
                .eq(UnSysMessage::getSystemId, context.systemId())
                .eq(UnSysMessage::getTenantId, context.tenantId())
                .eq(UnSysMessage::getRecipientMemberId, context.memberId())
                .eq(UnSysMessage::getStatus, "UNREAD")));
    }

    /** 已读时间首次读取时固定，重复点击不改写历史。 */
    @Transactional(rollbackFor = Exception.class)
    public MessageView read(String token, Long systemId, Long tenantId, Long messageId) {
        SystemContext context = systems.context(token, systemId, tenantId);
        UnSysMessage row = owned(context, messageId);
        if (!"ARCHIVED".equals(row.getStatus()) && !"READ".equals(row.getStatus())) {
            row.setStatus("READ"); row.setReadTime(LocalDateTime.now());
            messages.update(new LambdaUpdateWrapper<UnSysMessage>().eq(UnSysMessage::getId, row.getId())
                    .set(UnSysMessage::getStatus, "READ")
                    .set(UnSysMessage::getReadTime, row.getReadTime()));
        }
        return view(row);
    }

    /** 归档只改变本人的消息状态。 */
    @Transactional(rollbackFor = Exception.class)
    public void archive(String token, Long systemId, Long tenantId, Long messageId) {
        SystemContext context = systems.context(token, systemId, tenantId);
        UnSysMessage row = owned(context, messageId);
        if (!"ARCHIVED".equals(row.getStatus()))
            messages.update(new LambdaUpdateWrapper<UnSysMessage>().eq(UnSysMessage::getId, row.getId())
                    .set(UnSysMessage::getStatus, "ARCHIVED"));
    }

    private UnSysMessage owned(SystemContext context, Long id) {
        if (id == null || id < 1) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "消息主键无效");
        UnSysMessage row = messages.getOne(new LambdaQueryWrapper<UnSysMessage>()
                .eq(UnSysMessage::getId, id)
                .eq(UnSysMessage::getSystemId, context.systemId())
                .eq(UnSysMessage::getTenantId, context.tenantId())
                .eq(UnSysMessage::getRecipientMemberId, context.memberId()), false);
        if (row == null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "消息不存在");
        return row;
    }

    private String status(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("UNREAD", "READ", "ARCHIVED").contains(normalized))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "消息状态无效");
        return normalized;
    }

    private MessageView view(UnSysMessage row) {
        return new MessageView(row.getId(), row.getSystemId(), row.getTenantId(), row.getRecipientMemberId(),
                row.getTitle(), row.getContent(), row.getTargetType(), row.getTargetId(), row.getStatus(),
                row.getReadTime(), row.getCreateTime());
    }

}
