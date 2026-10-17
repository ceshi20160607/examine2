package com.unique.unexamine.message.manage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.unique.unexamine.message.base.entity.InboxMessage;
import com.unique.unexamine.message.base.service.InboxMessageBaseService;
import com.unique.unexamine.system.manage.SystemPermissionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

/** 系统内基础消息收件箱；只存通用标题和目标标识，目标详情仍由原业务接口鉴权。 */
@Service
public class MessageManageService {
    private final InboxMessageBaseService messages;
    private final SystemPermissionService access;

    public MessageManageService(InboxMessageBaseService messages, SystemPermissionService access) {
        this.messages = messages;
        this.access = access;
    }

    /** 审批事件在原事务中写消息；类别由服务端限定，不复制记录内容或审批意见。 */
    public void sendApproval(Long systemId, Long tenantId, Long recipientAccountId,
                             String kind, Long moduleId, Long recordId, Long taskId) {
        String title = switch (kind) {
            case "APPROVAL_TODO" -> "有一条新的审批待办";
            case "APPROVAL_APPROVED" -> "你的审批申请已通过";
            case "APPROVAL_REJECTED" -> "你的审批申请被拒绝";
            default -> throw new IllegalArgumentException("不支持的消息类别");
        };
        InboxMessage message = new InboxMessage();
        message.setSystemId(systemId);
        message.setTenantId(tenantId);
        message.setRecipientAccountId(recipientAccountId);
        message.setKind(kind);
        message.setTitle(title);
        message.setModuleId(moduleId);
        message.setRecordId(recordId);
        message.setTaskId(taskId);
        messages.insert(message);
    }

    /** 最近消息只按当前系统、租户和收件账号返回；移出系统后不能再读取。 */
    public List<MessageView> inbox(Long accountId, Long systemId) {
        SystemPermissionService.Context context = access.requireMembership(accountId, systemId);
        return messages.selectList(scope(accountId, systemId, context.tenant().getId())
                .orderByDesc(InboxMessage::getId).last("LIMIT 50"))
                .stream().map(MessageView::from).toList();
    }

    /** 顶栏显示的未读数，与列表使用同一收件范围。 */
    public long unreadCount(Long accountId, Long systemId) {
        SystemPermissionService.Context context = access.requireMembership(accountId, systemId);
        return messages.selectCount(scope(accountId, systemId, context.tenant().getId())
                .isNull(InboxMessage::getReadTime));
    }

    /** 首次已读时间只写一次；越权消息以不存在处理。 */
    public MessageView markRead(Long accountId, Long systemId, Long messageId) {
        SystemPermissionService.Context context = access.requireMembership(accountId, systemId);
        InboxMessage message = messages.selectById(messageId);
        if (message == null || !systemId.equals(message.getSystemId())
                || !context.tenant().getId().equals(message.getTenantId())
                || !accountId.equals(message.getRecipientAccountId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "消息不存在或无访问权限");
        }
        if (message.getReadTime() == null) {
            messages.update(new LambdaUpdateWrapper<InboxMessage>()
                    .eq(InboxMessage::getId, messageId).isNull(InboxMessage::getReadTime)
                    .set(InboxMessage::getReadTime, LocalDateTime.now()));
            message = messages.selectById(messageId);
        }
        return MessageView.from(message);
    }

    private LambdaQueryWrapper<InboxMessage> scope(Long accountId, Long systemId, Long tenantId) {
        return new LambdaQueryWrapper<InboxMessage>().eq(InboxMessage::getSystemId, systemId)
                .eq(InboxMessage::getTenantId, tenantId).eq(InboxMessage::getRecipientAccountId, accountId);
    }

    /** 消息列表只给出安全标题、已读状态及目标主键；查看目标时重新校验业务权限。 */
    public record MessageView(Long id, String kind, String title, Long moduleId, Long recordId,
                              Long taskId, LocalDateTime createTime, LocalDateTime readTime) {
        static MessageView from(InboxMessage message) {
            return new MessageView(message.getId(), message.getKind(), message.getTitle(), message.getModuleId(),
                    message.getRecordId(), message.getTaskId(), message.getCreateTime(), message.getReadTime());
        }
    }
}
