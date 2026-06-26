package com.unique.examine.aiwork.manage.work;

import com.unique.examine.core.api.PageResult;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 工作管理 API 请求和响应模型。
 */
public final class WorkManagementModels {

    private WorkManagementModels() {
    }

    /**
     * 工作管理固定标签。
     *
     * @param tabCode 标签编码
     * @param tabName 标签名称
     * @param routeName 前端路由
     * @param count 当前标签数量提示
     * @param selected 是否当前选中
     */
    public record WorkTab(String tabCode, String tabName, String routeName, Integer count, boolean selected) {
    }

    /**
     * 工作仪表盘。
     *
     * @param systemId 系统 ID
     * @param tenantId 租户 ID
     * @param tabs 固定四标签
     * @param overview 项目与任务概览
     * @param todayWarnings 今日预警列表
     * @param monthlyCalendar 当月日历数据
     * @param myTasks 我的待处理任务
     * @param dailyReportReminder 日报提醒
     * @param traceId 链路追踪 ID
     */
    public record WorkDashboardVO(String systemId, String tenantId, List<WorkTab> tabs, WorkOverview overview,
                                  List<WorkWarningVO> todayWarnings, List<CalendarDayVO> monthlyCalendar,
                                  List<WorkTaskVO> myTasks, DailyReportReminderVO dailyReportReminder,
                                  String traceId) {
    }

    /**
     * 工作概览。
     *
     * @param activeProjectCount 进行中的项目数
     * @param projectTaskCount 项目任务数
     * @param plainTaskCount 普通任务数
     * @param overdueTaskCount 逾期任务数
     * @param dailyReportSubmitted 今天是否已提交日报
     */
    public record WorkOverview(Integer activeProjectCount, Integer projectTaskCount, Integer plainTaskCount,
                               Integer overdueTaskCount, boolean dailyReportSubmitted) {
    }

    /**
     * 工作预警。
     *
     * @param warningId 预警 ID
     * @param warningType 预警类型
     * @param title 标题
     * @param level 级别
     * @param dueAt 截止时间
     * @param target 跳转目标
     */
    public record WorkWarningVO(String warningId, String warningType, String title, String level,
                                LocalDateTime dueAt, WorkTarget target) {
    }

    /**
     * 日历日期。
     *
     * @param date 日期
     * @param items 日期内的工作事项
     */
    public record CalendarDayVO(LocalDate date, List<CalendarItemVO> items) {
    }

    /**
     * 日历事项。
     *
     * @param itemId 事项 ID
     * @param itemType 事项类型
     * @param title 标题
     * @param color 颜色
     * @param target 跳转目标
     */
    public record CalendarItemVO(String itemId, String itemType, String title, String color, WorkTarget target) {
    }

    /**
     * 日报提醒。
     *
     * @param reportDate 日报日期
     * @param status 日报状态
     * @param canAutoDraft 是否可自动生成草稿
     * @param sourceCount 可用于草稿的来源数量
     * @param message 提醒文案
     */
    public record DailyReportReminderVO(LocalDate reportDate, String status, boolean canAutoDraft,
                                        Integer sourceCount, String message) {
    }

    /**
     * 项目查询请求。
     *
     * @param keyword 关键字
     * @param status 项目状态
     * @param ownerMemberId 负责人
     */
    public record ProjectSearchRequest(String keyword, String status, String ownerMemberId) {
    }

    public record ProjectCreateRequest(String projectCode, String projectName, String ownerMemberId, String status,
                                       Integer progress, LocalDate startDate, LocalDate endDate) {
    }

    /**
     * 项目视图。
     *
     * @param projectId 项目 ID
     * @param projectCode 项目编码
     * @param projectName 项目名称
     * @param owner 负责人
     * @param status 状态字典项
     * @param progress 完成度
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @param taskSummary 任务统计
     * @param rowDetailTarget 行点击详情目标
     */
    public record WorkProjectVO(String projectId, String projectCode, String projectName, MemberVO owner,
                                DictItemVO status, Integer progress, LocalDate startDate, LocalDate endDate,
                                TaskSummaryVO taskSummary, WorkTarget rowDetailTarget) {
    }

    /**
     * 任务统计。
     *
     * @param total 总数
     * @param doing 进行中
     * @param overdue 逾期
     * @param done 已完成
     */
    public record TaskSummaryVO(Integer total, Integer doing, Integer overdue, Integer done) {
    }

    /**
     * 任务查询请求。
     *
     * @param keyword 关键字
     * @param status 状态
     * @param assigneeMemberId 负责人
     * @param projectId 项目 ID
     * @param tagCodes 标签
     * @param dueRange 截止时间范围
     * @param filters 配置字段筛选
     */
    public record TaskSearchRequest(String keyword, String status, String assigneeMemberId, String projectId,
                                    List<String> tagCodes, String dueRange, Map<String, Object> filters) {
    }

    /**
     * 任务创建请求。
     *
     * @param title 标题
     * @param projectId 项目 ID
     * @param assigneeMemberId 负责人
     * @param collaborators 协作人
     * @param status 状态
     * @param tagCodes 标签
     * @param progress 完成度
     * @param dueAt 截止时间
     * @param relatedObject 关联对象
     * @param fieldValues 配置字段值
     */
    public record TaskCreateRequest(String title, String projectId, String assigneeMemberId,
                                    List<String> collaborators, String status, List<String> tagCodes,
                                    Integer progress, LocalDateTime dueAt, RelatedObjectVO relatedObject,
                                    Map<String, Object> fieldValues) {
    }

    /**
     * 任务更新请求。
     *
     * @param title 标题
     * @param assigneeMemberId 负责人
     * @param collaborators 协作人
     * @param status 状态
     * @param tagCodes 标签
     * @param progress 完成度
     * @param dueAt 截止时间
     * @param completedAt 完成时间
     * @param relatedObject 关联对象
     * @param fieldValues 配置字段值
     * @param changeReason 变更原因
     */
    public record TaskUpdateRequest(String title, String assigneeMemberId, List<String> collaborators,
                                    String status, List<String> tagCodes, Integer progress, LocalDateTime dueAt,
                                    LocalDateTime completedAt, RelatedObjectVO relatedObject,
                                    Map<String, Object> fieldValues, String changeReason) {
    }

    /**
     * 任务视图。
     *
     * @param taskId 任务 ID
     * @param taskType PROJECT 或 PLAIN
     * @param title 标题
     * @param projectId 项目 ID
     * @param projectName 项目名称
     * @param assignee 负责人
     * @param collaborators 协作人
     * @param status 状态字典项
     * @param tags 标签字典项
     * @param progress 完成度
     * @param dueAt 截止时间
     * @param completedAt 完成时间
     * @param relatedObject 关联对象
     * @param fieldValues 配置字段值
     * @param commentCount 评论数
     * @param warningLevel 预警级别
     * @param rowDetailTarget 行点击详情目标
     * @param actionPermissions 行动作权限
     * @param lifecycle 当前任务生命周期说明
     */
    public record WorkTaskVO(String taskId, String taskType, String title, String projectId, String projectName,
                             MemberVO assignee, List<MemberVO> collaborators, DictItemVO status,
                             List<DictItemVO> tags, Integer progress, LocalDateTime dueAt,
                             LocalDateTime completedAt, RelatedObjectVO relatedObject,
                             Map<String, Object> fieldValues, Integer commentCount, String warningLevel,
                             WorkTarget rowDetailTarget, List<ActionPermissionVO> actionPermissions,
                             TaskLifecycleVO lifecycle) {
    }

    /**
     * 任务生命周期。
     *
     * @param lifecycleCode 生命周期编码
     * @param allowedStatusCodes 可流转状态
     * @param separatedFrom 说明与另一类任务分离
     */
    public record TaskLifecycleVO(String lifecycleCode, List<String> allowedStatusCodes, String separatedFrom) {
    }

    /**
     * 看板查询请求。
     *
     * @param taskType PROJECT 或 PLAIN
     * @param columnFieldId 看板列字段 ID
     * @param swimlaneFieldId 泳道字段 ID
     * @param groupFieldId 分组字段 ID
     * @param pageCursor 游标
     */
    public record KanbanQueryRequest(String taskType, String columnFieldId, String swimlaneFieldId,
                                     String groupFieldId, String pageCursor) {
    }

    /**
     * 看板查询结果。
     *
     * @param systemId 系统 ID
     * @param tenantId 租户 ID
     * @param taskType 任务类型
     * @param config 使用的看板配置
     * @param columns 列数据
     * @param pageCursor 当前游标
     * @param nextCursor 下一页游标
     * @param hasMore 是否还有更多
     * @param traceId 链路追踪 ID
     */
    public record KanbanResultVO(String systemId, String tenantId, String taskType, KanbanConfigVO config,
                                 List<KanbanColumnVO> columns, String pageCursor, String nextCursor,
                                 boolean hasMore, String traceId) {
    }

    /**
     * 看板列。
     *
     * @param column 列对应字典项
     * @param cards 卡片
     * @param total 列总数
     * @param hasMore 是否还有更多
     */
    public record KanbanColumnVO(DictItemVO column, List<KanbanCardVO> cards, Integer total, boolean hasMore) {
    }

    /**
     * 看板卡片。
     *
     * @param taskId 任务 ID
     * @param title 标题
     * @param projectName 项目名称
     * @param assignee 负责人
     * @param status 状态
     * @param tags 标签
     * @param progress 完成度
     * @param dueAt 截止时间
     * @param swimlaneValue 泳道值
     * @param groupValue 分组值
     * @param warningLevel 预警级别
     * @param target 跳转目标
     */
    public record KanbanCardVO(String taskId, String title, String projectName, MemberVO assignee,
                               DictItemVO status, List<DictItemVO> tags, Integer progress, LocalDateTime dueAt,
                               DictItemVO swimlaneValue, DictItemVO groupValue, String warningLevel,
                               WorkTarget target) {
    }

    /**
     * 日报查询请求。
     *
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @param status 状态
     * @param keyword 关键字
     */
    public record DailyReportSearchRequest(LocalDate startDate, LocalDate endDate, String status, String keyword) {
    }

    /**
     * 日报创建请求。
     *
     * @param reportDate 日期
     * @param content 内容
     * @param status 状态
     * @param sourceIds 来源 ID
     * @param submitNow 是否直接提交
     */
    public record DailyReportCreateRequest(LocalDate reportDate, String content, String status,
                                           List<String> sourceIds, Boolean submitNow) {
    }

    /**
     * 日报视图。
     *
     * @param reportId 日报 ID
     * @param date 日期
     * @param status 状态
     * @param content 内容
     * @param sourceSummary 来源摘要
     * @param submitter 提交人
     * @param submittedAt 提交时间
     * @param permissionSnapshotId 权限快照
     * @param rowDetailTarget 行点击详情目标
     */
    public record DailyReportVO(String reportId, LocalDate date, String status, String content,
                                String sourceSummary, MemberVO submitter, LocalDateTime submittedAt,
                                String permissionSnapshotId, WorkTarget rowDetailTarget) {
    }

    /**
     * 自动日报草稿。
     *
     * @param draftId 草稿 ID
     * @param reportDate 日报日期
     * @param content 草稿内容
     * @param sources 来源明细
     * @param rule 自动来源规则
     * @param manualConfirmRequired 是否必须人工确认
     * @param permissionSnapshotId 权限快照
     * @param traceId 链路追踪 ID
     */
    public record DailyReportAutoDraftVO(String draftId, LocalDate reportDate, String content,
                                         List<DailyReportSourceVO> sources, DailyReportAutoSourceRuleVO rule,
                                         boolean manualConfirmRequired, String permissionSnapshotId,
                                         String traceId) {
    }

    /**
     * 日报来源。
     *
     * @param sourceId 来源 ID
     * @param sourceType 来源类型
     * @param title 标题
     * @param summary 摘要
     * @param occurredAt 发生时间
     * @param permissionPolicy 权限策略
     * @param target 跳转目标
     */
    public record DailyReportSourceVO(String sourceId, String sourceType, String title, String summary,
                                      LocalDateTime occurredAt, String permissionPolicy, WorkTarget target) {
    }

    /**
     * 评论创建请求。
     *
     * @param parentCommentId 父评论 ID
     * @param content 内容
     * @param attachments 附件
     */
    public record CommentCreateRequest(String parentCommentId, String content, List<FileRefVO> attachments) {
    }

    /**
     * 评论视图。
     *
     * @param commentId 评论 ID
     * @param parentCommentId 父评论 ID
     * @param commenter 评论人
     * @param content 内容
     * @param attachments 附件
     * @param createdAt 创建时间
     */
    public record TaskCommentVO(String commentId, String parentCommentId, MemberVO commenter, String content,
                                List<FileRefVO> attachments, LocalDateTime createdAt) {
    }

    /**
     * 任务事件。
     *
     * @param eventId 事件 ID
     * @param eventType 事件类型
     * @param beforePayload 变更前
     * @param afterPayload 变更后
     * @param operator 操作人
     * @param traceId 链路追踪 ID
     * @param auditLogId 审计日志 ID
     * @param createdAt 创建时间
     */
    public record TaskEventVO(String eventId, String eventType, Map<String, Object> beforePayload,
                              Map<String, Object> afterPayload, MemberVO operator, String traceId,
                              String auditLogId, LocalDateTime createdAt) {
    }

    /**
     * 工作配置更新请求。
     *
     * @param projectTaskFields 项目任务字段
     * @param plainTaskFields 普通任务字段
     * @param dailyReportFields 日报字段
     * @param projectTaskKanban 项目任务看板配置
     * @param plainTaskKanban 普通任务看板配置
     * @param dailyReportAutoSourceRule 日报自动草稿规则
     * @param changeReason 变更原因
     */
    public record WorkConfigUpdateRequest(List<WorkFieldConfigVO> projectTaskFields,
                                          List<WorkFieldConfigVO> plainTaskFields,
                                          List<WorkFieldConfigVO> dailyReportFields,
                                          KanbanConfigVO projectTaskKanban,
                                          KanbanConfigVO plainTaskKanban,
                                          DailyReportAutoSourceRuleVO dailyReportAutoSourceRule,
                                          String changeReason) {
    }

    /**
     * 工作配置。
     *
     * @param systemId 系统 ID
     * @param tenantId 租户 ID
     * @param projectTaskFields 项目任务字段
     * @param plainTaskFields 普通任务字段
     * @param dailyReportFields 日报字段
     * @param projectTaskKanban 项目任务看板配置
     * @param plainTaskKanban 普通任务看板配置
     * @param dailyReportAutoSourceRule 日报自动草稿规则
     * @param dictionaryBindings 字段字典绑定
     * @param statusColors 全局状态颜色语义
     * @param publishState 发布状态
     * @param updatedAt 更新时间
     */
    public record WorkConfigVO(String systemId, String tenantId, List<WorkFieldConfigVO> projectTaskFields,
                               List<WorkFieldConfigVO> plainTaskFields, List<WorkFieldConfigVO> dailyReportFields,
                               KanbanConfigVO projectTaskKanban, KanbanConfigVO plainTaskKanban,
                               DailyReportAutoSourceRuleVO dailyReportAutoSourceRule,
                               List<DictionaryBindingVO> dictionaryBindings, List<StatusColorSemanticVO> statusColors,
                               PublishStateVO publishState, LocalDateTime updatedAt) {
    }

    /**
     * 工作字段配置。
     *
     * @param fieldId 字段 ID
     * @param fieldCode 字段编码
     * @param fieldName 字段名称
     * @param fieldType 字段类型
     * @param required 是否必填
     * @param listVisible 列表是否显示
     * @param filterable 是否可筛选
     * @param sortable 是否可排序
     * @param dictTypeCode 绑定字典类型
     * @param dictItems 字典项
     * @param cardVisible 卡片是否显示
     * @param kanbanEligible 是否可作为看板列/泳道/分组
     * @param permissionCode 权限编码
     */
    public record WorkFieldConfigVO(String fieldId, String fieldCode, String fieldName, String fieldType,
                                    boolean required, boolean listVisible, boolean filterable, boolean sortable,
                                    String dictTypeCode, List<DictItemVO> dictItems, boolean cardVisible,
                                    boolean kanbanEligible, String permissionCode) {
    }

    /**
     * 看板配置。
     *
     * @param taskType 任务类型
     * @param columnField 列字段
     * @param swimlaneField 泳道字段
     * @param groupField 分组字段
     * @param cardFields 卡片字段
     * @param publishedVersion 发布版本
     */
    public record KanbanConfigVO(String taskType, WorkFieldRefVO columnField, WorkFieldRefVO swimlaneField,
                                 WorkFieldRefVO groupField, List<WorkFieldRefVO> cardFields,
                                 String publishedVersion) {
    }

    /**
     * 工作字段引用。
     *
     * @param fieldId 字段 ID
     * @param fieldCode 字段编码
     * @param fieldName 字段名称
     * @param fieldType 字段类型
     * @param dictTypeCode 字典类型
     * @param published 是否已发布
     */
    public record WorkFieldRefVO(String fieldId, String fieldCode, String fieldName, String fieldType,
                                 String dictTypeCode, boolean published) {
    }

    /**
     * 日报自动来源规则。
     *
     * @param sourceTypes 来源类型
     * @param taskScope 任务范围
     * @param todoScope 待办范围
     * @param messageScope 消息范围
     * @param logScope 业务日志范围
     * @param approvalScope 审批记录范围
     * @param permissionPolicy 权限策略
     * @param manualConfirmRequired 是否必须人工确认
     */
    public record DailyReportAutoSourceRuleVO(List<String> sourceTypes, String taskScope, String todoScope,
                                              String messageScope, String logScope, String approvalScope,
                                              String permissionPolicy, boolean manualConfirmRequired) {
    }

    /**
     * 配置发布检查结果。
     *
     * @param passed 是否通过
     * @param targetVersion 目标版本
     * @param items 检查项
     * @param impactRefs 影响对象
     * @param traceId 链路追踪 ID
     */
    public record WorkConfigPublishCheckResult(boolean passed, String targetVersion,
                                               List<PublishCheckItemVO> items, List<ImpactRefVO> impactRefs,
                                               String traceId) {
    }

    /**
     * 发布检查项。
     *
     * @param itemCode 检查编码
     * @param itemName 检查名称
     * @param level 等级
     * @param passed 是否通过
     * @param message 检查说明
     */
    public record PublishCheckItemVO(String itemCode, String itemName, String level, boolean passed,
                                     String message) {
    }

    /**
     * 影响对象。
     *
     * @param refType 对象类型
     * @param refId 对象 ID
     * @param refName 对象名称
     */
    public record ImpactRefVO(String refType, String refId, String refName) {
    }

    /**
     * 字典绑定。
     *
     * @param dictTypeCode 字典类型
     * @param dictTypeName 字典类型名称
     * @param ownerFieldIds 使用字段
     * @param items 字典项
     */
    public record DictionaryBindingVO(String dictTypeCode, String dictTypeName, List<String> ownerFieldIds,
                                      List<DictItemVO> items) {
    }

    /**
     * 字典项。
     *
     * @param itemCode 字典项编码
     * @param itemName 字典项名称
     * @param color 颜色
     * @param icon 图标
     * @param semantic 语义
     * @param sort 排序
     * @param enabled 是否启用
     * @param defaultItem 是否默认
     */
    public record DictItemVO(String itemCode, String itemName, String color, String icon, String semantic,
                             Integer sort, boolean enabled, boolean defaultItem) {
    }

    /**
     * 状态颜色语义。
     *
     * @param semantic 语义
     * @param color 颜色
     * @param description 说明
     */
    public record StatusColorSemanticVO(String semantic, String color, String description) {
    }

    /**
     * 发布状态。
     *
     * @param status 状态
     * @param version 版本
     * @param publishable 是否可发布
     * @param lastPublishedAt 最近发布时间
     */
    public record PublishStateVO(String status, String version, boolean publishable, LocalDateTime lastPublishedAt) {
    }

    /**
     * 成员视图。
     *
     * @param memberId 成员 ID
     * @param memberName 成员姓名
     * @param avatar 头像
     * @param departmentName 部门名称
     */
    public record MemberVO(String memberId, String memberName, String avatar, String departmentName) {
    }

    /**
     * 关联对象。
     *
     * @param objectType 对象类型
     * @param objectId 对象 ID
     * @param objectTitle 对象标题
     * @param target 跳转目标
     */
    public record RelatedObjectVO(String objectType, String objectId, String objectTitle, WorkTarget target) {
    }

    /**
     * 跳转目标。
     *
     * @param targetType 目标类型
     * @param routeName 前端路由
     * @param params 路由参数
     */
    public record WorkTarget(String targetType, String routeName, Map<String, String> params) {
    }

    /**
     * 动作权限。
     *
     * @param actionCode 动作编码
     * @param actionName 动作名称
     * @param enabled 是否可用
     * @param disabledReason 禁用原因
     * @param resultDrawerCode 结果承接抽屉
     */
    public record ActionPermissionVO(String actionCode, String actionName, boolean enabled,
                                     String disabledReason, String resultDrawerCode) {
    }

    /**
     * 任务保存结果。
     *
     * @param task 任务视图
     * @param event 任务事件
     * @param traceId 链路追踪 ID
     * @param auditLogId 审计日志 ID
     */
    public record TaskMutationResult(WorkTaskVO task, TaskEventVO event, String traceId, String auditLogId) {
    }

    /**
     * 文件引用。
     *
     * @param fileId 文件 ID
     * @param fileName 文件名
     * @param fileType 文件类型
     * @param downloadUrl 下载地址
     */
    public record FileRefVO(String fileId, String fileName, String fileType, String downloadUrl) {
    }

    /**
     * 分页结果别名，方便控制器方法签名表达业务语义。
     *
     * @param page 原始分页结果
     * @param <T> 数据类型
     */
    public record WorkPage<T>(PageResult<T> page) {
    }
}
