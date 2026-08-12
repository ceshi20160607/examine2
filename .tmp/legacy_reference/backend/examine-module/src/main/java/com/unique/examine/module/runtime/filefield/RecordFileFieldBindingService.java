package com.unique.examine.module.runtime.filefield;

import com.fasterxml.jackson.databind.JsonNode;
import com.unique.examine.core.api.ApiError;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Canonical bridge between module field values and the existing file centre.
 * File bytes remain owned by examine-file; this service only validates scoped
 * ACTIVE assets and maintains deletion-protecting field references atomically
 * with the record mutation.
 */
@Service
public class RecordFileFieldBindingService {
    public static final Set<String> TYPES = Set.of("ATTACHMENT", "IMAGE", "FILE_GROUP", "SIGNATURE");
    public static final String TARGET_TYPE = "MODULE_RECORD_FIELD";

    private final JdbcTemplate jdbc;

    public RecordFileFieldBindingService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Produces only the file bindings touched by a record patch. Omitted fields
     * are deliberately absent; an explicitly supplied empty field is present
     * with an empty list and therefore remains an intentional clear.
     */
    public static Map<Long, List<Long>> touchedBindings(Map<String, Long> writableFileFields,
                                                        Set<String> suppliedFieldCodes,
                                                        Map<Long, List<Long>> desired) {
        var touched = new LinkedHashMap<Long, List<Long>>();
        writableFileFields.forEach((code, fieldId) -> {
            if (suppliedFieldCodes.contains(code)) {
                touched.put(fieldId, List.copyOf(desired.getOrDefault(fieldId, List.of())));
            }
        });
        return Map.copyOf(touched);
    }

    public List<AssetValue> resolve(RuntimeSession session, String fieldCode, String fieldType,
                                    JsonNode schema, JsonNode supplied) {
        if (!TYPES.contains(fieldType)) throw new IllegalArgumentException("Not a file field: " + fieldType);
        requirePermissions(session);
        if (!supplied.isArray()) throw invalid(fieldCode, "文件字段必须提交 fileId 数组");
        var maxFiles = Math.max(1, Math.min(schema.path("maxFiles").asInt(defaultMaximum(fieldType)), 100));
        if (supplied.size() > maxFiles || ("SIGNATURE".equals(fieldType) && supplied.size() > 1)) {
            throw invalid(fieldCode, "文件数量超过字段配置上限 " + maxFiles);
        }
        var ids = new LinkedHashSet<Long>();
        for (var item : supplied) {
            var value = item.isObject() ? item.path("fileId") : item;
            long id;
            try {
                id = value.isIntegralNumber() ? value.longValue() : Long.parseLong(value.asText());
            } catch (RuntimeException exception) {
                throw invalid(fieldCode, "文件标识无效");
            }
            if (id <= 0 || !ids.add(id)) throw invalid(fieldCode, "文件标识必须为正数且不能重复");
        }
        if (ids.isEmpty()) return List.of();
        var parameters = new ArrayList<Object>();
        parameters.add(session.systemId());
        parameters.add(requiredTenant(session));
        parameters.addAll(ids);
        var rows = jdbc.query("SELECT id,original_name,media_type,size_bytes,sha256 FROM un_file_object "
                        + "WHERE system_id=? AND tenant_id=? AND status='ACTIVE' AND id IN ("
                        + placeholders(ids.size()) + ") ORDER BY FIELD(id," + placeholders(ids.size()) + ")",
                (result, row) -> new AssetValue(result.getLong("id"), result.getString("original_name"),
                        result.getString("media_type"), result.getLong("size_bytes"), result.getString("sha256")),
                concat(parameters, ids).toArray());
        if (rows.size() != ids.size()) throw invalid(fieldCode, "文件不存在或不属于当前系统/租户");
        var byId = new java.util.HashMap<Long, AssetValue>();
        rows.forEach(row -> byId.put(row.fileId(), row));
        var ordered = ids.stream().map(byId::get).toList();
        validatePolicy(fieldCode, fieldType, schema, ordered);
        return List.copyOf(ordered);
    }

    public void synchronize(RuntimeSession session, long recordId, long fieldId,
                            List<Long> desiredFileIds, LocalDateTime now) {
        requirePermissions(session);
        var targetId = targetId(recordId, fieldId);
        var tenantId = requiredTenant(session);
        var desired = new LinkedHashSet<>(desiredFileIds == null ? List.of() : desiredFileIds);
        if (desired.isEmpty()) {
            jdbc.update("DELETE FROM un_file_reference WHERE system_id=? AND tenant_id=? "
                            + "AND target_type=? AND target_id=?",
                    session.systemId(), tenantId, TARGET_TYPE, targetId);
            return;
        }
        var parameters = new ArrayList<Object>();
        parameters.add(session.systemId());
        parameters.add(tenantId);
        parameters.add(TARGET_TYPE);
        parameters.add(targetId);
        parameters.addAll(desired);
        jdbc.update("DELETE FROM un_file_reference WHERE system_id=? AND tenant_id=? "
                        + "AND target_type=? AND target_id=? AND file_id NOT IN ("
                        + placeholders(desired.size()) + ")", parameters.toArray());
        for (var fileId : desired) {
            jdbc.update("INSERT IGNORE INTO un_file_reference "
                            + "(system_id,tenant_id,file_id,target_type,target_id,created_by_member_id,created_at) "
                            + "VALUES (?,?,?,?,?,?,?)",
                    session.systemId(), tenantId, fileId, TARGET_TYPE, targetId, session.memberId(), now);
        }
    }

    private static void requirePermissions(RuntimeSession session) {
        if (!session.permissions().contains("file.read")
                || !(session.permissions().contains("file.reference") || session.permissions().contains("file.manage"))) {
            throw new BusinessException("FILE_FIELD_FORBIDDEN",
                    "文件字段需要 file.read 与 file.reference 权限", HttpStatus.FORBIDDEN);
        }
    }

    private static void validatePolicy(String fieldCode, String fieldType, JsonNode schema, List<AssetValue> values) {
        var imageOnly = "IMAGE".equals(fieldType) || "SIGNATURE".equals(fieldType)
                || schema.path("imageOnly").asBoolean(false);
        var maxBytes = Math.max(1, schema.path("maxSizeMb").asLong(100)) * 1024L * 1024L;
        var allowed = new LinkedHashSet<String>();
        schema.path("allowedExtensions").forEach(value -> allowed.add(value.asText("").toLowerCase()
                .replaceFirst("^\\.", "")));
        for (var value : values) {
            if (value.sizeBytes() > maxBytes) throw invalid(fieldCode, "文件超过字段大小上限");
            if (imageOnly && !value.mediaType().toLowerCase().startsWith("image/")) {
                throw invalid(fieldCode, "图片或签名字段只允许图片文件");
            }
            if (!allowed.isEmpty()) {
                var dot = value.originalName().lastIndexOf('.');
                var extension = dot < 0 ? "" : value.originalName().substring(dot + 1).toLowerCase();
                if (!allowed.contains(extension)) throw invalid(fieldCode, "文件扩展名不在字段允许范围内");
            }
        }
    }

    private static int defaultMaximum(String type) {
        return "IMAGE".equals(type) ? 20 : "SIGNATURE".equals(type) ? 1 : 10;
    }

    private static long requiredTenant(RuntimeSession session) {
        if (session.tenantId() == null || session.tenantId() <= 0) {
            throw new BusinessException("CONTEXT_TENANT_REQUIRED", "当前请求缺少有效租户上下文",
                    HttpStatus.FORBIDDEN);
        }
        return session.tenantId();
    }

    private static String targetId(long recordId, long fieldId) {
        return recordId + ":" + fieldId;
    }

    private static List<Object> concat(List<Object> prefix, Iterable<Long> suffix) {
        var values = new ArrayList<>(prefix);
        suffix.forEach(values::add);
        return values;
    }

    private static String placeholders(int size) {
        return String.join(",", java.util.Collections.nCopies(size, "?"));
    }

    private static BusinessException invalid(String fieldCode, String message) {
        return new BusinessException("FILE_FIELD_VALUE_INVALID", message, HttpStatus.UNPROCESSABLE_ENTITY,
                List.of(new ApiError("FILE_FIELD_VALUE_INVALID", "values." + fieldCode, message)));
    }

    public record AssetValue(long fileId, String originalName, String mediaType, long sizeBytes, String sha256) { }
}
