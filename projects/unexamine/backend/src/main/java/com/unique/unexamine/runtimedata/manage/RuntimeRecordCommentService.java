package com.unique.unexamine.runtimedata.manage;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.notification.manage.MessageService;
import com.unique.unexamine.runtimedata.base.entity.BizRecordComment;
import com.unique.unexamine.runtimedata.base.entity.BusinessRecord;
import com.unique.unexamine.runtimedata.base.entity.BusinessRecordParticipant;
import com.unique.unexamine.runtimedata.base.service.BizRecordCommentBaseService;
import com.unique.unexamine.runtimedata.base.service.BusinessRecordBaseService;
import com.unique.unexamine.runtimedata.base.service.BusinessRecordParticipantBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.system.base.entity.SystemMember;
import com.unique.unexamine.system.base.entity.SystemTenantMember;
import com.unique.unexamine.system.base.service.SystemMemberBaseService;
import com.unique.unexamine.system.base.service.SystemTenantMemberBaseService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 评论随真实业务记录归属保存，读写权限沿用当前记录详情校验。 */
@Service
public class RuntimeRecordCommentService {
    private final RuntimeDataService runtimeDataService;
    private final BusinessRecordBaseService recordService;
    private final BizRecordCommentBaseService commentService;
    private final BusinessRecordParticipantBaseService participantService;
    private final SystemMemberBaseService memberService;
    private final SystemTenantMemberBaseService tenantMemberService;
    private final AuditRecorder auditRecorder;
    private final MessageService messageService;

    public RuntimeRecordCommentService(RuntimeDataService runtimeDataService,
                                       BusinessRecordBaseService recordService,
                                       BizRecordCommentBaseService commentService,
                                       BusinessRecordParticipantBaseService participantService,
                                       SystemMemberBaseService memberService,
                                       SystemTenantMemberBaseService tenantMemberService,
                                       AuditRecorder auditRecorder,
                                       MessageService messageService) {
        this.runtimeDataService = runtimeDataService;
        this.recordService = recordService;
        this.commentService = commentService;
        this.participantService = participantService;
        this.memberService = memberService;
        this.tenantMemberService = tenantMemberService;
        this.auditRecorder = auditRecorder;
        this.messageService = messageService;
    }

    /** 评论列表以详情权限为界，并按真实记录的来源租户过滤评论。 */
    @Transactional
    public RuntimeRecordCommentModels.Page list(AuthenticatedContext context, String moduleCode, Long recordId,
                                                int page, int pageSize, String traceId) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new DomainException("COMMENT_PAGE_INVALID", "评论分页参数无效", HttpStatus.BAD_REQUEST);
        }
        BusinessRecord record = readableRecord(context, moduleCode, recordId, traceId);
        IPage<BizRecordComment> saved = commentService.selectPage(new Page<>(page, pageSize),
                Wrappers.<BizRecordComment>lambdaQuery()
                        .eq(BizRecordComment::getSystemId, record.getSystemId())
                        .eq(BizRecordComment::getTenantId, record.getTenantId())
                        .eq(BizRecordComment::getModuleId, record.getModuleId())
                        .eq(BizRecordComment::getRecordId, recordId)
                        .eq(BizRecordComment::getDeleted, false)
                        .orderByDesc(BizRecordComment::getCreatedTime, BizRecordComment::getId));
        Set<Long> authorIds = saved.getRecords().stream().map(BizRecordComment::getCreatedByMemberId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> names = authorIds.isEmpty() ? Map.of() : memberService.selectList(
                Wrappers.<SystemMember>lambdaQuery().eq(SystemMember::getSystemId, context.systemId())
                        .in(SystemMember::getId, authorIds)).stream()
                .collect(Collectors.toMap(SystemMember::getId, SystemMember::getDisplayName));
        return new RuntimeRecordCommentModels.Page(saved.getRecords().stream()
                .map(comment -> view(comment, names.get(comment.getCreatedByMemberId()))).toList(),
                saved.getTotal(), page, pageSize);
    }

    /** 只允许来源租户成员评论；回复必须指向当前记录中未删除的评论。 */
    @Transactional
    public RuntimeRecordCommentModels.View create(AuthenticatedContext context, String moduleCode, Long recordId,
                                                  RuntimeRecordCommentModels.CreateRequest input, String traceId) {
        BusinessRecord record = readableRecord(context, moduleCode, recordId, traceId);
        // 旧评论保留可读；新评论及回复受模块当前发布的协作功能开关约束。
        if (!runtimeDataService.commentsEnabled(context, moduleCode)) {
            throw new DomainException("MODULE_COMMENT_DISABLED", "当前模块未开放评论", HttpStatus.FORBIDDEN);
        }
        if (!record.getTenantId().equals(context.tenantId())) {
            throw new DomainException("COMMENT_SOURCE_TENANT_REQUIRED", "共享记录暂不允许跨租户发表评论",
                    HttpStatus.FORBIDDEN);
        }
        if (context.memberId() == null || Boolean.TRUE.equals(record.getDeleted())
                || Boolean.TRUE.equals(record.getArchived())) {
            throw new DomainException("COMMENT_RECORD_UNAVAILABLE", "当前记录不可评论", HttpStatus.CONFLICT);
        }
        BizRecordComment parent = input.parentCommentId() == null ? null : commentService.selectOne(
                Wrappers.<BizRecordComment>lambdaQuery()
                        .eq(BizRecordComment::getId, input.parentCommentId())
                        .eq(BizRecordComment::getSystemId, record.getSystemId())
                        .eq(BizRecordComment::getTenantId, record.getTenantId())
                        .eq(BizRecordComment::getModuleId, record.getModuleId())
                        .eq(BizRecordComment::getRecordId, recordId)
                        .eq(BizRecordComment::getDeleted, false));
        if (input.parentCommentId() != null && parent == null) {
            throw new DomainException("COMMENT_PARENT_NOT_FOUND", "回复的评论不存在", HttpStatus.NOT_FOUND);
        }
        BizRecordComment comment = new BizRecordComment();
        comment.setSystemId(record.getSystemId());
        comment.setTenantId(record.getTenantId());
        comment.setModuleId(record.getModuleId());
        comment.setRecordId(recordId);
        comment.setParentCommentId(input.parentCommentId());
        comment.setBody(input.body().strip());
        comment.setCreatedByMemberId(context.memberId());
        comment.setCreatedTime(LocalDateTime.now());
        comment.setUpdatedTime(comment.getCreatedTime());
        comment.setDeleted(false);
        comment.setVersion(0);
        commentService.insert(comment);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("recordId", recordId);
        detail.put("moduleCode", moduleCode);
        detail.put("parentCommentId", input.parentCommentId());
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                input.parentCommentId() == null ? "BUSINESS_RECORD_COMMENT_ADDED" : "BUSINESS_RECORD_COMMENT_REPLIED",
                "BUSINESS_RECORD_COMMENT", String.valueOf(comment.getId()), "SUCCESS", detail);
        // 评论、操作审计和站内通知共用事务，通知落库失败时不能留下半成功评论。
        messageService.notifyRuntimeRecordComment(context, moduleCode, recordId, comment.getId(),
                parent != null, recipientAccountIds(record, parent, context.memberId()));
        return view(comment, context.displayName());
    }

    /** 普通评论通知当前团队；回复只通知被回复作者，均排除发送者和失效的来源租户成员。 */
    private List<Long> recipientAccountIds(BusinessRecord record, BizRecordComment parent, Long senderMemberId) {
        LinkedHashSet<Long> memberIds = new LinkedHashSet<>();
        if (parent != null) {
            memberIds.add(parent.getCreatedByMemberId());
        } else {
            if (record.getOwnerMemberId() != null) memberIds.add(record.getOwnerMemberId());
            participantService.selectList(Wrappers.<BusinessRecordParticipant>lambdaQuery()
                    .eq(BusinessRecordParticipant::getSystemId, record.getSystemId())
                    .eq(BusinessRecordParticipant::getTenantId, record.getTenantId())
                    .eq(BusinessRecordParticipant::getRecordId, record.getId())
                    .in(BusinessRecordParticipant::getParticipantType, "COLLABORATOR", "READ_ONLY", "FOLLOWER"))
                    .forEach(item -> memberIds.add(item.getSystemMemberId()));
        }
        memberIds.remove(senderMemberId);
        memberIds.remove(null);
        if (memberIds.isEmpty()) return List.of();
        Set<Long> activeTenantIds = tenantMemberService.selectList(Wrappers.<SystemTenantMember>lambdaQuery()
                        .eq(SystemTenantMember::getSystemId, record.getSystemId())
                        .eq(SystemTenantMember::getTenantId, record.getTenantId())
                        .eq(SystemTenantMember::getStatus, "ACTIVE")
                        .in(SystemTenantMember::getSystemMemberId, memberIds)).stream()
                .map(SystemTenantMember::getSystemMemberId).collect(Collectors.toSet());
        if (activeTenantIds.isEmpty()) return List.of();
        return memberService.selectList(Wrappers.<SystemMember>lambdaQuery()
                        .eq(SystemMember::getSystemId, record.getSystemId())
                        .eq(SystemMember::getStatus, "ACTIVE")
                        .in(SystemMember::getId, activeTenantIds)).stream()
                .map(SystemMember::getAccountId).filter(Objects::nonNull).distinct().toList();
    }

    /** 复用详情服务的发布模块、数据范围和共享校验，再读物理记录以取得来源租户和模块主键。 */
    private BusinessRecord readableRecord(AuthenticatedContext context, String moduleCode, Long recordId,
                                          String traceId) {
        RuntimeRecordView visible = runtimeDataService.detail(context, moduleCode, recordId, traceId);
        BusinessRecord record = recordService.selectById(recordId);
        if (record == null || !Objects.equals(record.getSystemId(), context.systemId())
                || !Objects.equals(record.getTenantId(), visible.dataTenantId())) {
            throw new DomainException("RECORD_NOT_FOUND", "业务数据不存在", HttpStatus.NOT_FOUND);
        }
        return record;
    }

    private RuntimeRecordCommentModels.View view(BizRecordComment comment, String authorName) {
        return new RuntimeRecordCommentModels.View(comment.getId(), comment.getRecordId(),
                comment.getParentCommentId(), comment.getBody(), comment.getCreatedByMemberId(),
                authorName == null ? "成员 " + comment.getCreatedByMemberId() : authorName,
                comment.getCreatedTime());
    }
}
