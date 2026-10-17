package com.unique.unexamine.message.manage;

import com.unique.unexamine.platform.manage.AccountSessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 当前系统内的本人消息列表、未读数和已读操作。 */
@RestController
@RequestMapping("/api/systems/{systemId}/messages")
public class MessageManageController {
    private final AccountSessionService sessions;
    private final MessageManageService messages;

    public MessageManageController(AccountSessionService sessions, MessageManageService messages) {
        this.sessions = sessions;
        this.messages = messages;
    }

    /** 只返回当前账号在当前系统最近 50 条消息。 */
    @GetMapping
    public List<MessageManageService.MessageView> inbox(@PathVariable Long systemId, HttpServletRequest request) {
        return messages.inbox(accountId(request), systemId);
    }

    /** 查询当前账号未读消息数量。 */
    @GetMapping("/unread-count")
    public UnreadCount unreadCount(@PathVariable Long systemId, HttpServletRequest request) {
        return new UnreadCount(messages.unreadCount(accountId(request), systemId));
    }

    /** 标记本人消息已读，不替代目标记录或待办的权限验证。 */
    @PostMapping("/{messageId}/read")
    public MessageManageService.MessageView markRead(@PathVariable Long systemId, @PathVariable Long messageId,
                                                     HttpServletRequest request) {
        return messages.markRead(accountId(request), systemId, messageId);
    }

    private Long accountId(HttpServletRequest request) { return sessions.requireAccount(request).getId(); }

    /** 未读数响应字段。 */
    public record UnreadCount(long count) {}
}
