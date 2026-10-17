package com.unique.unexamine.foundation.manage.control;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.runtimedata.manage.CreateRuntimeRecordRequest;
import com.unique.unexamine.runtimedata.manage.RecordLifecycleRequest;
import com.unique.unexamine.runtimedata.manage.RuntimeDataService;
import com.unique.unexamine.runtimedata.manage.UpdateRuntimeRecordRequest;
import com.unique.unexamine.foundation.base.service.CoreEventOutboxBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RuntimeDataCommandExtension {
    private static final String CREATE_OPERATION = "RUNTIME_RECORD_CREATE_DIRECT";
    private static final String UPDATE_OPERATION = "RUNTIME_RECORD_UPDATE_DIRECT";
    private static final String DELETE_OPERATION = "RUNTIME_RECORD_DELETE_DIRECT";

    private final RuntimeDataService runtimeDataService;
    private final ObjectMapper objectMapper;
    private final CoreEventOutboxBaseService outboxService;

    public RuntimeDataCommandExtension(
            RuntimeDataService runtimeDataService,
            ObjectMapper objectMapper, CoreEventOutboxBaseService outboxService) {
        this.runtimeDataService = runtimeDataService;
        this.objectMapper = objectMapper;
        this.outboxService = outboxService;
    }

    /** Compatibility adapter: execute synchronously through the same runtime boundary as the page. */
    public FoundationControlModels.CommandResult execute(AuthenticatedContext context,
            FoundationControlModels.SubmitCommandRequest command, String traceId) {
        String operationCode = command.operationCode().strip().toUpperCase(java.util.Locale.ROOT);
        if (!Set.of(CREATE_OPERATION, UPDATE_OPERATION, DELETE_OPERATION).contains(operationCode)) {
            throw new DomainException("FOUNDATION_OPERATION_NOT_ALLOWED", "不支持该业务动作", HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> input = command.payload();
        if (!"DIRECT".equals(input.getOrDefault("executionMode", "DIRECT"))) {
            throw new DomainException("ACTION_MODE_SERVER_CONTROLLED", "执行模式由已发布的动作配置决定", HttpStatus.BAD_REQUEST);
        }
        String moduleCode = text(input.get("moduleCode"));
        if (moduleCode.isBlank()) {
            throw new DomainException("FOUNDATION_RUNTIME_COMMAND_MODULE_REQUIRED", "请选择业务模块", HttpStatus.BAD_REQUEST);
        }
        BusinessAction.Options options = new BusinessAction.Options(command.idempotencyKey(), "COMMAND",
                command.applicationCode() == null || command.applicationCode().isBlank() ? "runtime" : command.applicationCode(), null, 0);
        Long recordId = asLong(input.get("recordId"));
        if (!CREATE_OPERATION.equals(operationCode) && recordId == null) {
            throw new DomainException("FOUNDATION_RUNTIME_COMMAND_RECORD_ID_REQUIRED", "请选择业务数据", HttpStatus.BAD_REQUEST);
        }
        var result = switch (operationCode) {
            case CREATE_OPERATION -> runtimeDataService.createAction(context, moduleCode, buildCreateRequest(input), traceId, options);
            case UPDATE_OPERATION -> runtimeDataService.updateAction(context, moduleCode, recordId, buildUpdateRequest(input), traceId, options);
            case DELETE_OPERATION -> {
                if (input.get("lifecycleAction") != null && !"DELETE".equalsIgnoreCase(text(input.get("lifecycleAction")))) {
                    throw new DomainException("FOUNDATION_RUNTIME_COMMAND_INVALID_LIFECYCLE_ACTION", "删除入口只接受删除动作", HttpStatus.BAD_REQUEST);
                }
                yield runtimeDataService.deleteAction(context, moduleCode, recordId, buildRecordLifecycleRequest(input), traceId, options);
            }
            default -> throw new IllegalStateException("Unknown action");
        };
        var event = outboxService.selectById(result.outboxId());
        return new FoundationControlModels.CommandResult(result.idempotencyRecordId(), result.outboxId(),
                result.idempotencyKey(), operationCode, result.value() == null ? "flow-instance:" + result.flowInstanceId()
                        : "business-record:" + result.value().id(), result.status(),
                result.replayed(), event.getCreateTime().toString(), event.getStatus());
    }

    private CreateRuntimeRecordRequest buildCreateRequest(Map<String, Object> payload) {
        String title = textOrNull(payload.get("title"));
        Map<String, Object> fields = asMap(payload.get("fields"));
        if (fields == null) {
            fields = Map.of();
        }
        Map<String, JsonNode> normalizedFields = new LinkedHashMap<>();
        fields.forEach((fieldCode, value) -> normalizedFields.put(fieldCode, objectMapper.valueToTree(value)));

        List<Long> participants = asLongList(payload.get("participantMemberIds"));
        if (participants == null) {
            participants = new ArrayList<>();
        }
        // 通用动作与表单、导入、应用共用创建人规则；缺失值留给运行态按当前系统所属人补齐。
        return new CreateRuntimeRecordRequest(
                title,
                textOrNull(payload.get("recordNumber")),
                textOrNull(payload.get("status")),
                asLong(payload.get("ownerMemberId")),
                asLong(payload.get("departmentId")),
                participants,
                normalizedFields,
                null,
                asLong(payload.get("createdByMemberId")));
    }

    private UpdateRuntimeRecordRequest buildUpdateRequest(Map<String, Object> payload) {
        String title = textOrNull(payload.get("title"));
        Map<String, Object> fields = asMap(payload.get("fields"));
        if (fields == null) {
            fields = Map.of();
        }
        Map<String, JsonNode> normalizedFields = new LinkedHashMap<>();
        fields.forEach((fieldCode, value) -> normalizedFields.put(fieldCode, objectMapper.valueToTree(value)));

        List<Long> participants = asLongList(payload.get("participantMemberIds"));
        if (participants == null) {
            participants = new ArrayList<>();
        }
        Integer version = asInteger(payload.get("version"));
        if (version == null) {
            throw new DomainException("FOUNDATION_RUNTIME_COMMAND_VERSION_REQUIRED",
                    "更新命令缺少 version", HttpStatus.BAD_REQUEST);
        }
        return new UpdateRuntimeRecordRequest(
                title,
                textOrNull(payload.get("recordNumber")),
                textOrNull(payload.get("status")),
                asLong(payload.get("ownerMemberId")),
                asLong(payload.get("departmentId")),
                participants,
                normalizedFields,
                version);
    }

    private RecordLifecycleRequest buildRecordLifecycleRequest(Map<String, Object> payload) {
        Integer version = asInteger(payload.get("version"));
        if (version == null) {
            throw new DomainException("FOUNDATION_RUNTIME_COMMAND_VERSION_REQUIRED",
                    "生命周期命令缺少 version", HttpStatus.BAD_REQUEST);
        }
        String reason = textOrNull(payload.get("reason"));
        if (reason == null) {
            reason = "系统运行命令";
        }
        return new RecordLifecycleRequest(reason, version, asLong(payload.get("replacementRecordId")));
    }

    private Map<String, Object> asMap(Object value) {
        if (value == null) return null;
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new DomainException("ACTION_INPUT_INVALID", "字段值必须是对象", HttpStatus.BAD_REQUEST);
    }

    private String text(Object value) {
        if (value == null) return "";
        return String.valueOf(value).strip();
    }

    private String textOrNull(Object value) {
        String text = text(value);
        return text.isBlank() ? null : text;
    }

    private Long asLong(Object value) {
        if (value == null) return null;
        try {
            return new java.math.BigDecimal(text(value)).longValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new DomainException("ACTION_INPUT_INVALID", "数据或人员标识必须是有效整数", HttpStatus.BAD_REQUEST);
        }
    }

    private Integer asInteger(Object value) {
        if (value == null) return null;
        try {
            return Math.toIntExact(asLong(value));
        } catch (ArithmeticException exception) {
            throw new DomainException("ACTION_INPUT_INVALID", "版本号超出有效范围", HttpStatus.BAD_REQUEST);
        }
    }

    private List<Long> asLongList(Object value) {
        if (value == null) return null;
        if (!(value instanceof List<?> source) || source.stream().anyMatch(java.util.Objects::isNull)) {
            throw new DomainException("ACTION_INPUT_INVALID", "相关人员必须是有效标识列表", HttpStatus.BAD_REQUEST);
        }
        return source.stream().map(this::asLong).toList();
    }
}
