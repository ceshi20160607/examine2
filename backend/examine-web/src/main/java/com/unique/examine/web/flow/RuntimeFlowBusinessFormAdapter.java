package com.unique.examine.web.flow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.flow.extension.FlowBusinessFormPort;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.runtime.security.RuntimeSession;
import com.unique.examine.module.runtime.service.RecordRuntimeService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RuntimeFlowBusinessFormAdapter implements FlowBusinessFormPort {
    private final RecordRuntimeService records;
    private final ObjectMapper json;

    public RuntimeFlowBusinessFormAdapter(
            RecordRuntimeService records,
            ObjectMapper json) {
        this.records = records;
        this.json = json;
    }

    @Override
    public FormRecord read(Actor actor, String moduleCode, long recordId) {
        var session = session(actor);
        return view(moduleCode, records.detail(session, moduleCode, recordId),
                records.schema(session, moduleCode));
    }

    @Override
    public FormRecord update(
            Actor actor,
            String moduleCode,
            long recordId,
            long expectedVersion,
            Map<String, JsonNode> values,
            String idempotencyKey,
            String requestId,
            String traceId) {
        var session = session(actor);
        var current = records.detail(session, moduleCode, recordId);
        var updated = records.update(
                session, moduleCode, recordId,
                new RecordRuntimeViews.UpdateRecordRequest(
                        current.schemaVersionId(), current.title(), expectedVersion,
                        values, List.of(), List.of()),
                idempotencyKey, requestId, traceId);
        return view(moduleCode, updated, records.schema(session, moduleCode));
    }

    @Override
    public FormRecord create(
            Actor actor,
            String moduleCode,
            String title,
            Map<String, JsonNode> values,
            String idempotencyKey,
            String requestId,
            String traceId) {
        var session = session(actor);
        var schema = records.schema(session, moduleCode);
        var created = records.create(
                session, moduleCode,
                new RecordRuntimeViews.CreateRecordRequest(
                        schema.schemaVersionId(), title, values, List.of(), List.of()),
                idempotencyKey, requestId, traceId);
        return view(moduleCode, created, schema);
    }

    private FormRecord view(
            String moduleCode,
            RecordRuntimeViews.RecordDetail detail,
            RecordRuntimeViews.RecordSchema schema) {
        var values = new LinkedHashMap<String, JsonNode>();
        for (var field : detail.values()) {
            values.put(field.fieldCode(), json.valueToTree(field.value()));
        }
        var capabilities = new LinkedHashMap<String, FieldCapability>();
        for (var field : schema.fields()) {
            capabilities.put(field.fieldCode(),
                    new FieldCapability(field.readable(), field.writable()));
        }
        return new FormRecord(moduleCode, Long.parseLong(detail.recordId()),
                detail.version(), detail.title(), detail.schemaVersionId(),
                values, capabilities);
    }

    private static RuntimeSession session(Actor actor) {
        return new RuntimeSession(actor.accountId(), actor.systemId(), actor.memberId(),
                actor.tenantId(), actor.permissions());
    }
}
