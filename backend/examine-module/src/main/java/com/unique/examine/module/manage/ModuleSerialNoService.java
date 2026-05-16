package com.unique.examine.module.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.module.entity.po.ModuleField;
import com.unique.examine.module.entity.po.ModuleSerialSeq;
import com.unique.examine.module.field.ModuleFieldConfigSupport;
import com.unique.examine.module.field.ModuleFieldType;
import com.unique.examine.module.mapper.ModuleSerialSeqMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
/**

 * 按字段 config_json.segments 生成自定义编号。
 */
@Service
public class ModuleSerialNoService {

    @Autowired
    private ModuleSerialSeqMapper moduleSerialSeqMapper;
    @Autowired
    private ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public String generate(long systemId,
                           long tenantId,
                           long appId,
                           long modelId,
                           ModuleField field,
                           JsonNode data) {
        ModuleFieldType type = ModuleFieldConfigSupport.typeOf(field);
        if (type != ModuleFieldType.SERIAL_NO) {
            throw new BusinessException(400, "非 SERIAL_NO 字段");
        }
        if (field.getConfigJson() == null || field.getConfigJson().isBlank()) {
            throw new BusinessException(400, "SERIAL_NO 缺少 config_json.segments");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(field.getConfigJson());
        } catch (Exception e) {
            throw new BusinessException(400, "SERIAL_NO config_json 非法");
        }
        JsonNode segments = root.get("segments");
        if (segments == null || !segments.isArray() || segments.isEmpty()) {
            throw new BusinessException(400, "SERIAL_NO segments 不能为空");
        }

        String resetKey = "never";
        int seqWidth = 4;
        for (JsonNode seg : segments) {
            if (seg != null && "seq".equalsIgnoreCase(seg.path("type").asText())) {
                String reset = seg.path("reset").asText("never");
                resetKey = resetKeyFor(reset);
                if (seg.has("width")) {
                    seqWidth = Math.max(1, Math.min(12, seg.get("width").asInt(4)));
                }
                break;
            }
        }

        long seq = nextSeq(systemId, tenantId, appId, modelId, field.getFieldCode(), resetKey);

        StringBuilder sb = new StringBuilder();
        for (JsonNode seg : segments) {
            if (seg == null || seg.isNull()) {
                continue;
            }
            String t = seg.path("type").asText("");
            switch (t) {
                case "fixed" -> sb.append(seg.path("value").asText(""));
                case "field" -> {
                    String fc = seg.path("fieldCode").asText("");
                    if (!fc.isBlank() && data != null && data.isObject()) {
                        JsonNode v = data.get(fc);
                        if (v != null && !v.isNull()) {
                            sb.append(v.asText(""));
                        }
                    }
                }
                case "seq" -> sb.append(zeroPad(seq, seqWidth));
                default -> throw new BusinessException(400, "未知 segment.type: " + t);
            }
        }
        return sb.toString();
    }

    private long nextSeq(long systemId, long tenantId, long appId, long modelId, String fieldCode, String resetKey) {
        ModuleSerialSeq row = moduleSerialSeqMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ModuleSerialSeq>()
                        .eq(ModuleSerialSeq::getSystemId, systemId)
                        .eq(ModuleSerialSeq::getTenantId, tenantId)
                        .eq(ModuleSerialSeq::getModelId, modelId)
                        .eq(ModuleSerialSeq::getFieldCode, fieldCode)
                        .eq(ModuleSerialSeq::getResetKey, resetKey)
                        .last("FOR UPDATE"));
        if (row == null) {
            row = new ModuleSerialSeq();
            row.setSystemId(systemId);
            row.setTenantId(tenantId);
            row.setAppId(appId);
            row.setModelId(modelId);
            row.setFieldCode(fieldCode);
            row.setResetKey(resetKey);
            row.setSeqNo(1L);
            moduleSerialSeqMapper.insert(row);
            return 1L;
        }
        long next = (row.getSeqNo() == null ? 0L : row.getSeqNo()) + 1L;
        row.setSeqNo(next);
        moduleSerialSeqMapper.updateById(row);
        return next;
    }

    private static String resetKeyFor(String reset) {
        if (reset == null) {
            return "never";
        }
        return switch (reset.trim().toLowerCase()) {
            case "day", "daily" -> LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
            case "month", "monthly" -> LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
            default -> "never";
        };
    }

    private static String zeroPad(long n, int width) {
        String s = String.valueOf(n);
        if (s.length() >= width) {
            return s;
        }
        return "0".repeat(width - s.length()) + s;
    }
}
