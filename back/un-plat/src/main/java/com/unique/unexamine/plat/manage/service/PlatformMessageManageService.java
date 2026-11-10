package com.unique.unexamine.plat.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.plat.base.entity.UnPlatMessage;
import com.unique.unexamine.plat.base.service.IUnPlatMessageService;
import com.unique.unexamine.plat.manage.dto.PlatformManageDto.MessageSearch;
import com.unique.unexamine.plat.manage.dto.PlatformManageDto.MessageView;
import com.unique.unexamine.plat.manage.dto.PlatformManageDto.UnreadSummary;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 平台站内收件箱；账号只能读取和更新自己的消息，发送由模板服务执行。 */
@Service
public class PlatformMessageManageService {
    private final AuthManageService auth;
    private final IUnPlatMessageService messages;

    /** 注入账号身份和消息基础服务。 */
    public PlatformMessageManageService(AuthManageService auth, IUnPlatMessageService messages) {
        this.auth = auth;
        this.messages = messages;
    }

    /** 当前账号按未读状态和标题内容分页读取消息。 */
    public Page<MessageView> mine(String token, MessageSearch search) {
        var account = auth.requireAccount(token);
        if (search == null) search = new MessageSearch();
        var where = new LambdaQueryWrapper<UnPlatMessage>().eq(UnPlatMessage::getRecipientAccountId, account.getId())
                .orderByDesc(UnPlatMessage::getId);
        String status = search.getStatus();
        if (status != null && !status.isBlank()) {
            String normalized = status.trim().toUpperCase();
            if (!java.util.Set.of("UNREAD", "READ", "ARCHIVED").contains(normalized))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "消息状态无效");
            where.eq(UnPlatMessage::getStatus, normalized);
        }
        String keyword = search.getKeyword();
        if (keyword != null && !keyword.isBlank()) {
            String text = keyword.trim();
            if (text.length() > 100) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "消息搜索内容过长");
            where.and(q -> q.like(UnPlatMessage::getTitle, text).or().like(UnPlatMessage::getContent, text));
        }
        Page<UnPlatMessage> result = messages.page(search.parse(), where);
        return new Page<MessageView>(result.getCurrent(), result.getSize(), result.getTotal())
                .setRecords(result.getRecords().stream().map(this::view).toList());
    }

    /** 顶部角标按当前平台账号的未读消息计算。 */
    public UnreadSummary unread(String token) {
        return unread(auth.requireAccount(token).getId());
    }

    /** 当前账号全部已读，只更新未读行并返回最新未读数量。 */
    @Transactional(rollbackFor = Exception.class)
    public UnreadSummary readAll(String token) {
        Long accountId = auth.requireAccount(token).getId();
        messages.update(new LambdaUpdateWrapper<UnPlatMessage>()
                .eq(UnPlatMessage::getRecipientAccountId, accountId)
                .eq(UnPlatMessage::getStatus, "UNREAD")
                .set(UnPlatMessage::getStatus, "READ")
                .set(UnPlatMessage::getReadTime, LocalDateTime.now()));
        return unread(accountId);
    }

    private UnreadSummary unread(Long accountId) {
        return new UnreadSummary(messages.count(new LambdaQueryWrapper<UnPlatMessage>()
                .eq(UnPlatMessage::getRecipientAccountId, accountId)
                .eq(UnPlatMessage::getStatus, "UNREAD")));
    }

    /** 当前账号标记自己的消息已读；重复标记保持幂等。 */
    @Transactional(rollbackFor = Exception.class)
    public MessageView read(String token, Long messageId) {
        var account = auth.requireAccount(token);
        UnPlatMessage message = own(messageId, account.getId());
        if (!"ARCHIVED".equals(message.getStatus())) {
            message.setStatus("READ");
            message.setReadTime(message.getReadTime() == null ? LocalDateTime.now() : message.getReadTime());
            messages.update(new LambdaUpdateWrapper<UnPlatMessage>().eq(UnPlatMessage::getId, messageId)
                    .set(UnPlatMessage::getStatus, message.getStatus()).set(UnPlatMessage::getReadTime, message.getReadTime()));
        }
        return view(message);
    }

    /** 当前账号归档自己的消息，归档后不再出现在默认未读列表。 */
    @Transactional(rollbackFor = Exception.class)
    public void archive(String token, Long messageId) {
        var account = auth.requireAccount(token);
        own(messageId, account.getId());
        messages.update(new LambdaUpdateWrapper<UnPlatMessage>().eq(UnPlatMessage::getId, messageId)
                .set(UnPlatMessage::getStatus, "ARCHIVED"));
    }

    private UnPlatMessage own(Long id, Long accountId) {
        if (id == null || id < 1) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "消息主键无效");
        }
        UnPlatMessage message = messages.getOne(new LambdaQueryWrapper<UnPlatMessage>()
                .eq(UnPlatMessage::getId, id).eq(UnPlatMessage::getRecipientAccountId, accountId), false);
        if (message == null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "消息不存在");
        return message;
    }

    private MessageView view(UnPlatMessage message) {
        return new MessageView(message.getId(), message.getRecipientAccountId(), message.getTitle(), message.getContent(),
                message.getTargetType(), message.getTargetId(), message.getStatus(), message.getReadTime(), message.getCreateTime());
    }

}
