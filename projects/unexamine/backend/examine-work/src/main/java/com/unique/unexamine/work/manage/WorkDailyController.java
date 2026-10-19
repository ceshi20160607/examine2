package com.unique.unexamine.work.manage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.unique.unexamine.sys.base.entity.UnSysTenantMember;
import com.unique.unexamine.work.base.entity.UnWorkDaily;
import com.unique.unexamine.work.base.entity.UnWorkDailyComment;
import com.unique.unexamine.work.base.entity.UnWorkDailyField;
import com.unique.unexamine.work.base.mapper.UnWorkDailyCommentMapper;
import com.unique.unexamine.work.base.mapper.UnWorkDailyFieldMapper;
import com.unique.unexamine.work.base.mapper.UnWorkDailyMapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.sys.manage.SystemSessionAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 租户日报字段、个人填写、提交及管理反馈。 */
@Tag(name = "工作日报", description = "日报填写、提交和评论。")
@RestController
@RequestMapping("/api/tenants/{tenantId}/work/dailies")
public class WorkDailyController {
    private final SystemSessionAccess access;
    private final UnWorkDailyMapper dailies;
    private final UnWorkDailyFieldMapper fields;
    private final UnWorkDailyCommentMapper comments;
    private final ObjectMapper json;

    public WorkDailyController(SystemSessionAccess access, UnWorkDailyMapper dailies,
                               UnWorkDailyFieldMapper fields, UnWorkDailyCommentMapper comments, ObjectMapper json) {
        this.access = access;
        this.dailies = dailies;
        this.fields = fields;
        this.comments = comments;
        this.json = json;
    }

    /** 日报字段只能由租户管理员配置，编码稳定供既有日报使用。 */
    @Operation(summary = "新建组件字段（工作日报）")
    @PostMapping("/fields")
    public FieldView addField(@PathVariable long tenantId, @Valid @RequestBody FieldInput input,
                              HttpServletRequest request) {
        access.tenantAdmin(request, tenantId);
        if (!input.fieldKey().matches("[a-z][a-z0-9_]{0,63}")
                || !List.of("text", "number", "date", "select", "boolean").contains(input.fieldType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "字段编码或类型无效");
        }
        if ("select".equals(input.fieldType()) && (input.options() == null || input.options().isEmpty())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "单选字段需要可选值");
        }
        UnWorkDailyField field = new UnWorkDailyField();
        field.setTenantId(tenantId);
        field.setFieldKey(input.fieldKey());
        field.setFieldName(input.fieldName().trim());
        field.setFieldType(input.fieldType());
        field.setRequiredFlag(input.required() ? (byte) 1 : (byte) 0);
        field.setOptionData(input.options() == null ? null : stringify(input.options()));
        field.setSortNo(input.sortNo() == null ? 0 : input.sortNo());
        field.setStatus((byte) 1);
        fields.insert(field);
        return fieldView(field);
    }

    @Operation(summary = "查询字段（工作日报）")
    @GetMapping("/fields")
    public List<FieldView> fields(@PathVariable long tenantId, HttpServletRequest request) {
        access.tenantMember(request, tenantId);
        return fields.selectList(new LambdaQueryWrapper<UnWorkDailyField>()
                        .eq(UnWorkDailyField::getTenantId, tenantId)
                        .orderByAsc(UnWorkDailyField::getSortNo, UnWorkDailyField::getId))
                .stream().map(this::fieldView).toList();
    }

    @Operation(summary = "修改字段（工作日报）")
    @PutMapping("/fields/{fieldId}")
    public FieldView updateField(@PathVariable long tenantId, @PathVariable long fieldId,
                                 @Valid @RequestBody FieldUpdate input, HttpServletRequest request) {
        access.tenantAdmin(request, tenantId);
        UnWorkDailyField field = fields.selectById(fieldId);
        if (field == null || !field.getTenantId().equals(tenantId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "日报字段不存在");
        }
        field.setFieldName(input.fieldName().trim());
        field.setRequiredFlag(input.required() ? (byte) 1 : (byte) 0);
        field.setSortNo(input.sortNo() == null ? 0 : input.sortNo());
        field.setStatus(input.enabled() ? (byte) 1 : (byte) 0);
        fields.updateById(field);
        return fieldView(field);
    }

    /** 默认只返回自己的日报，管理员可指定成员查看租户内日报。 */
    @Operation(summary = "查询列表（工作日报）")
    @GetMapping
    public DailyPage list(@PathVariable long tenantId, @RequestParam(required = false) Long memberId,
                          @RequestParam(defaultValue = "1") long page,
                          @RequestParam(defaultValue = "20") long size,
                          @RequestParam(defaultValue = "workDate") String sortField,
                          @RequestParam(defaultValue = "desc") String sortOrder,
                          HttpServletRequest request) {
        UnSysTenantMember actor = access.tenantMember(request, tenantId);
        if (page < 1 || size < 1 || size > 100 || !List.of("workDate", "createTime").contains(sortField)
                || !List.of("asc", "desc").contains(sortOrder.toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页或排序参数无效");
        }
        long targetMemberId = memberId == null ? actor.getMemberId() : memberId;
        if (targetMemberId != actor.getMemberId()) {
            access.tenantAdmin(request, tenantId);
        }
        boolean asc = "asc".equalsIgnoreCase(sortOrder);
        LambdaQueryWrapper<UnWorkDaily> query = new LambdaQueryWrapper<UnWorkDaily>()
                .eq(UnWorkDaily::getTenantId, tenantId).eq(UnWorkDaily::getMemberId, targetMemberId);
        if ("createTime".equals(sortField)) {
            query.orderBy(true, asc, UnWorkDaily::getCreateTime);
        } else {
            query.orderBy(true, asc, UnWorkDaily::getWorkDate);
        }
        query.orderByAsc(UnWorkDaily::getId);
        Page<UnWorkDaily> result = dailies.selectPage(new Page<>(page, size), query);
        return new DailyPage(result.getRecords().stream().map(this::view).toList(), result.getTotal(), page, size);
    }

    @Operation(summary = "新建（工作日报）")
    @PostMapping
    public DailyView create(@PathVariable long tenantId, @Valid @RequestBody DailyInput input,
                            HttpServletRequest request) {
        UnSysTenantMember actor = access.tenantMember(request, tenantId);
        UnWorkDaily daily = new UnWorkDaily();
        daily.setTenantId(tenantId);
        daily.setMemberId(actor.getMemberId());
        daily.setWorkDate(input.workDate());
        daily.setContent(input.content().trim());
        daily.setFieldData(validatedData(tenantId, input.fieldData()));
        daily.setStatus("draft");
        dailies.insert(daily);
        return view(daily);
    }

    @Operation(summary = "查询详情（工作日报）")
    @GetMapping("/{dailyId}")
    public DailyView detail(@PathVariable long tenantId, @PathVariable long dailyId,
                            HttpServletRequest request) {
        return view(requireReadableDaily(tenantId, dailyId, request));
    }

    /** 已提交的日报不能再编辑，修改须保留管理反馈的真实历史。 */
    @Operation(summary = "修改（工作日报）")
    @PutMapping("/{dailyId}")
    public DailyView update(@PathVariable long tenantId, @PathVariable long dailyId,
                            @Valid @RequestBody DailyInput input, HttpServletRequest request) {
        UnWorkDaily daily = requireOwnDraft(tenantId, dailyId, request);
        daily.setWorkDate(input.workDate());
        daily.setContent(input.content().trim());
        daily.setFieldData(validatedData(tenantId, input.fieldData()));
        dailies.updateById(daily);
        return view(daily);
    }

    @Operation(summary = "提交日报（工作日报）")
    @PostMapping("/{dailyId}/submit")
    public DailyView submit(@PathVariable long tenantId, @PathVariable long dailyId,
                            HttpServletRequest request) {
        UnWorkDaily daily = requireOwnDraft(tenantId, dailyId, request);
        daily.setStatus("submitted");
        dailies.updateById(daily);
        return view(daily);
    }

    @Operation(summary = "发表评论（工作日报）")
    @PostMapping("/{dailyId}/comments")
    public CommentView comment(@PathVariable long tenantId, @PathVariable long dailyId,
                               @Valid @RequestBody CommentInput input, HttpServletRequest request) {
        UnWorkDaily daily = requireReadableDaily(tenantId, dailyId, request);
        if (!"submitted".equals(daily.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "草稿日报不能评论");
        }
        UnWorkDailyComment comment = new UnWorkDailyComment();
        comment.setDailyId(dailyId);
        comment.setMemberId(access.systemMember(request).getId());
        comment.setContent(input.content().trim());
        comments.insert(comment);
        return new CommentView(comment.getId(), comment.getMemberId(), comment.getContent(), comment.getCreateTime());
    }

    @Operation(summary = "查询评论（工作日报）")
    @GetMapping("/{dailyId}/comments")
    public List<CommentView> comments(@PathVariable long tenantId, @PathVariable long dailyId,
                                      HttpServletRequest request) {
        requireReadableDaily(tenantId, dailyId, request);
        return comments.selectList(new LambdaQueryWrapper<UnWorkDailyComment>()
                        .eq(UnWorkDailyComment::getDailyId, dailyId)
                        .orderByAsc(UnWorkDailyComment::getId))
                .stream().map(item -> new CommentView(item.getId(), item.getMemberId(),
                        item.getContent(), item.getCreateTime())).toList();
    }

    private UnWorkDaily requireReadableDaily(long tenantId, long dailyId, HttpServletRequest request) {
        UnSysTenantMember actor = access.tenantMember(request, tenantId);
        UnWorkDaily daily = dailies.selectById(dailyId);
        if (daily == null || !daily.getTenantId().equals(tenantId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "日报不存在");
        }
        if (!daily.getMemberId().equals(actor.getMemberId())) {
            access.tenantAdmin(request, tenantId);
        }
        return daily;
    }

    private UnWorkDaily requireOwnDraft(long tenantId, long dailyId, HttpServletRequest request) {
        UnWorkDaily daily = requireReadableDaily(tenantId, dailyId, request);
        if (!daily.getMemberId().equals(access.systemMember(request).getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能修改自己的日报");
        }
        if (!"draft".equals(daily.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "日报已提交");
        }
        return daily;
    }

    private String validatedData(long tenantId, Map<String, Object> values) {
        Map<String, Object> submitted = values == null ? Map.of() : values;
        Map<String, UnWorkDailyField> definitions = new HashMap<>();
        for (UnWorkDailyField field : fields.selectList(new LambdaQueryWrapper<UnWorkDailyField>()
                .eq(UnWorkDailyField::getTenantId, tenantId).eq(UnWorkDailyField::getStatus, 1))) {
            definitions.put(field.getFieldKey(), field);
            Object value = submitted.get(field.getFieldKey());
            if (field.getRequiredFlag() != null && field.getRequiredFlag() == 1
                    && (value == null || value instanceof String text && text.isBlank())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "必填字段缺失：" + field.getFieldKey());
            }
        }
        for (var item : submitted.entrySet()) {
            UnWorkDailyField field = definitions.get(item.getKey());
            if (field == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未知或停用的日报字段：" + item.getKey());
            }
            Object value = item.getValue();
            if (value == null) {
                continue;
            }
            boolean valid = switch (field.getFieldType()) {
                case "text" -> value instanceof String;
                case "select" -> value instanceof String text && allowedOption(field, text);
                case "number" -> value instanceof Number;
                case "boolean" -> value instanceof Boolean;
                case "date" -> value instanceof String text && isDate(text);
                default -> false;
            };
            if (!valid) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "日报字段值无效：" + field.getFieldKey());
            }
        }
        return stringify(submitted);
    }

    private boolean isDate(String value) {
        try {
            LocalDate.parse(value);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private boolean allowedOption(UnWorkDailyField field, String value) {
        try {
            List<String> options = json.readValue(field.getOptionData(), new TypeReference<>() { });
            return options.contains(value);
        } catch (JsonProcessingException ex) {
            return false;
        }
    }

    private String stringify(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "字段数据无法保存");
        }
    }

    private FieldView fieldView(UnWorkDailyField field) {
        return new FieldView(field.getId(), field.getFieldKey(), field.getFieldName(), field.getFieldType(),
                field.getRequiredFlag(), field.getOptionData(), field.getSortNo(), field.getStatus());
    }

    private DailyView view(UnWorkDaily daily) {
        return new DailyView(daily.getId(), daily.getMemberId(), daily.getWorkDate(), daily.getContent(),
                daily.getFieldData(), daily.getStatus(), daily.getCreateTime());
    }

    public record FieldInput(@NotBlank String fieldKey, @NotBlank @Size(max = 100) String fieldName,
                             @NotBlank String fieldType, boolean required, List<String> options, Integer sortNo) { }
    public record FieldUpdate(@NotBlank @Size(max = 100) String fieldName,
                              boolean required, boolean enabled, Integer sortNo) { }
    public record FieldView(Long id, String fieldKey, String fieldName, String fieldType,
                            Byte required, String optionsJson, Integer sortNo, Byte status) { }
    public record DailyInput(@NotNull LocalDate workDate, @NotBlank String content,
                             Map<String, Object> fieldData) { }
    public record DailyView(Long id, Long memberId, LocalDate workDate, String content,
                            String fieldDataJson, String status, LocalDateTime createTime) { }
    public record DailyPage(List<DailyView> records, long total, long page, long size) { }
    public record CommentInput(@NotBlank @Size(max = 10000) String content) { }
    public record CommentView(Long id, Long memberId, String content, LocalDateTime createTime) { }
}
