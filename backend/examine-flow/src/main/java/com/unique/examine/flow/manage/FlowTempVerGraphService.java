package com.unique.examine.flow.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.flow.entity.po.FlowTempVer;
import com.unique.examine.flow.entity.po.FlowTempVerLine;
import com.unique.examine.flow.entity.po.FlowTempVerLineCond;
import com.unique.examine.flow.entity.po.FlowTempVerNode;
import com.unique.examine.flow.service.IFlowTempVerLineCondService;
import com.unique.examine.flow.service.IFlowTempVerLineService;
import com.unique.examine.flow.service.IFlowTempVerNodeService;
import com.unique.examine.flow.service.IFlowTempVerService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FlowTempVerGraphService {

    @Autowired
    private IFlowTempVerService flowTempVerService;
    @Autowired
    private IFlowTempVerNodeService flowTempVerNodeService;
    @Autowired
    private IFlowTempVerLineService flowTempVerLineService;
    @Autowired
    private IFlowTempVerLineCondService flowTempVerLineCondService;
    @Autowired
    private ObjectMapper objectMapper;

    public Map<String, Object> loadDesignerGraph(Long tempVerId, long systemId, long tenantId) {
        FlowTempVer ver = requireVer(tempVerId, systemId, tenantId);
        List<FlowTempVerNode> dbNodes = listNodes(tempVerId, systemId, tenantId);
        List<FlowTempVerLine> dbLines = listLines(tempVerId, systemId, tenantId);

        if (dbNodes.isEmpty() && ver.getGraphJson() != null && !ver.getGraphJson().isBlank()) {
            importFromGraphJson(ver, systemId, tenantId);
            dbNodes = listNodes(tempVerId, systemId, tenantId);
            dbLines = listLines(tempVerId, systemId, tenantId);
        }

        Map<Long, List<FlowTempVerLineCond>> condsByLine = loadCondsByLines(dbLines);
        List<Map<String, Object>> nodes = new ArrayList<>();
        int idx = 0;
        for (FlowTempVerNode n : dbNodes) {
            nodes.add(toDesignerNode(n, idx++));
        }
        List<Map<String, Object>> edges = new ArrayList<>();
        for (FlowTempVerLine l : dbLines) {
            edges.add(toDesignerEdge(l, condsByLine.get(l.getId())));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tempVerId", tempVerId);
        out.put("tempId", ver.getTempId());
        out.put("verNo", ver.getVerNo());
        out.put("publishStatus", ver.getPublishStatus());
        out.put("graphJson", ver.getGraphJson());
        out.put("nodes", nodes);
        out.put("edges", edges);
        return out;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> saveDesignerGraph(Long tempVerId, SaveGraphBody body, long systemId, long tenantId, long platId) {
        if (body == null) {
            throw new BusinessException(400, "body 不能为空");
        }
        FlowTempVer ver = requireVer(tempVerId, systemId, tenantId);
        List<DesignerNode> nodes = body.nodes() == null ? List.of() : body.nodes();
        List<DesignerEdge> edges = body.edges() == null ? List.of() : body.edges();

        replaceAll(tempVerId, systemId, tenantId, platId, nodes, edges);

        String graphJson = buildGraphJson(nodes, edges, body.graphConfigJson());
        ver.setGraphJson(graphJson);
        ver.setUpdateUserId(platId);
        flowTempVerService.updateById(ver);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tempVerId", tempVerId);
        out.put("graphJson", graphJson);
        return out;
    }

    private void replaceAll(Long tempVerId, long systemId, long tenantId, long platId,
                            List<DesignerNode> nodes, List<DesignerEdge> edges) {
        List<FlowTempVerLine> oldLines = listLines(tempVerId, systemId, tenantId);
        for (FlowTempVerLine l : oldLines) {
            flowTempVerLineCondService.lambdaUpdate().eq(FlowTempVerLineCond::getLineId, l.getId()).remove();
        }
        flowTempVerLineService.lambdaUpdate()
                .eq(FlowTempVerLine::getSystemId, systemId)
                .eq(FlowTempVerLine::getTenantId, tenantId)
                .eq(FlowTempVerLine::getTempVerId, tempVerId)
                .remove();
        flowTempVerNodeService.lambdaUpdate()
                .eq(FlowTempVerNode::getSystemId, systemId)
                .eq(FlowTempVerNode::getTenantId, tenantId)
                .eq(FlowTempVerNode::getTempVerId, tempVerId)
                .remove();

        Map<String, Long> keyToNodeId = new HashMap<>();
        int sort = 0;
        for (DesignerNode dn : nodes) {
            if (dn.nodeKey() == null || dn.nodeKey().isBlank()) {
                continue;
            }
            FlowTempVerNode n = new FlowTempVerNode();
            n.setSystemId(systemId);
            n.setTenantId(tenantId);
            n.setTempVerId(tempVerId);
            n.setNodeKey(dn.nodeKey().trim());
            n.setNodeType(dn.nodeType() == null || dn.nodeType().isBlank() ? "approve" : dn.nodeType().trim());
            n.setNodeName(dn.nodeName() == null ? dn.nodeKey() : dn.nodeName().trim());
            n.setParentNodeKey(dn.parentNodeKey());
            n.setSortNo(dn.sortNo() == null ? sort++ : dn.sortNo());
            n.setStatus(1);
            n.setConfigJson(mergeLayoutConfig(dn));
            n.setCreateUserId(platId);
            n.setUpdateUserId(platId);
            flowTempVerNodeService.save(n);
            keyToNodeId.put(n.getNodeKey(), n.getId());
        }

        for (DesignerEdge de : edges) {
            if (de.fromNodeKey() == null || de.toNodeKey() == null) {
                continue;
            }
            String from = de.fromNodeKey().trim();
            String to = de.toNodeKey().trim();
            if (!keyToNodeId.containsKey(from) || !keyToNodeId.containsKey(to)) {
                continue;
            }
            FlowTempVerLine l = new FlowTempVerLine();
            l.setSystemId(systemId);
            l.setTenantId(tenantId);
            l.setTempVerId(tempVerId);
            l.setFromNodeKey(from);
            l.setToNodeKey(to);
            l.setPriority(de.priority() == null ? 0 : de.priority());
            l.setIsDefault(de.isDefault() == null ? 0 : de.isDefault());
            l.setStatus(1);
            l.setRemark(de.cond());
            l.setCreateUserId(platId);
            l.setUpdateUserId(platId);
            flowTempVerLineService.save(l);
            saveLineCondFromEdge(l.getId(), de.cond(), platId);
        }
    }

    private void saveLineCondFromEdge(Long lineId, String cond, long platId) {
        if (cond == null || cond.isBlank()) {
            return;
        }
        ParsedCond pc = parseCondExpr(cond.trim());
        if (pc == null) {
            return;
        }
        FlowTempVerLineCond c = new FlowTempVerLineCond();
        c.setLineId(lineId);
        c.setGroupNo(0);
        c.setLogicOp("AND");
        c.setLeftVar(pc.leftVar());
        c.setCmpOp(pc.cmpOp());
        c.setRightType(pc.rightType());
        c.setRightValue(pc.rightValue());
        c.setStatus(1);
        c.setCreateUserId(platId);
        c.setUpdateUserId(platId);
        flowTempVerLineCondService.save(c);
    }

    private String buildGraphJson(List<DesignerNode> nodes, List<DesignerEdge> edges, String graphConfigJson) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            ArrayNode nodesArr = objectMapper.createArrayNode();
            for (DesignerNode dn : nodes) {
                if (dn.nodeKey() == null || dn.nodeKey().isBlank()) {
                    continue;
                }
                ObjectNode n = objectMapper.createObjectNode();
                n.put("id", dn.nodeKey().trim());
                n.put("type", dn.nodeType() == null ? "approve" : dn.nodeType().trim());
                n.put("name", dn.nodeName() == null ? dn.nodeKey() : dn.nodeName());
                JsonNode cfg = parseConfigJson(mergeLayoutConfig(dn));
                if (cfg != null && cfg.isObject()) {
                    n.set("config", cfg);
                }
                nodesArr.add(n);
            }
            root.set("nodes", nodesArr);

            ArrayNode edgesArr = objectMapper.createArrayNode();
            for (DesignerEdge de : edges) {
                if (de.fromNodeKey() == null || de.toNodeKey() == null) {
                    continue;
                }
                ObjectNode e = objectMapper.createObjectNode();
                e.put("from", de.fromNodeKey().trim());
                e.put("to", de.toNodeKey().trim());
                if (de.priority() != null) {
                    e.put("priority", de.priority());
                }
                if (de.cond() != null && !de.cond().isBlank()) {
                    e.put("cond", de.cond().trim());
                }
                edgesArr.add(e);
            }
            root.set("edges", edgesArr);

            JsonNode cfg = parseConfigJson(graphConfigJson);
            if (cfg != null && cfg.isObject()) {
                root.set("config", cfg);
            } else {
                ObjectNode def = objectMapper.createObjectNode();
                ObjectNode ep = objectMapper.createObjectNode();
                ep.put("mode", "fallback_admin");
                ep.put("admin_plat_id", 0);
                def.set("exception_policy", ep);
                root.set("config", def);
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new BusinessException(400, "生成 graphJson 失败: " + e.getMessage());
        }
    }

    private void importFromGraphJson(FlowTempVer ver, long systemId, long tenantId) {
        try {
            JsonNode root = objectMapper.readTree(ver.getGraphJson());
            JsonNode nodes = root.get("nodes");
            if (nodes == null || !nodes.isArray()) {
                return;
            }
            int i = 0;
            for (JsonNode n : nodes) {
                String key = text(n, "id");
                if (key == null) {
                    continue;
                }
                FlowTempVerNode node = new FlowTempVerNode();
                node.setSystemId(systemId);
                node.setTenantId(tenantId);
                node.setTempVerId(ver.getId());
                node.setNodeKey(key);
                node.setNodeType(text(n, "type") == null ? "approve" : text(n, "type"));
                node.setNodeName(text(n, "name") == null ? key : text(n, "name"));
                node.setSortNo(i++);
                node.setStatus(1);
                JsonNode cfg = n.get("config");
                if (cfg != null) {
                    node.setConfigJson(cfg.toString());
                } else {
                    node.setConfigJson(defaultLayoutJson(i));
                }
                flowTempVerNodeService.save(node);
            }
            JsonNode edges = root.get("edges");
            if (edges != null && edges.isArray()) {
                for (JsonNode e : edges) {
                    String from = text(e, "from");
                    String to = text(e, "to");
                    if (from == null || to == null) {
                        continue;
                    }
                    FlowTempVerLine line = new FlowTempVerLine();
                    line.setSystemId(systemId);
                    line.setTenantId(tenantId);
                    line.setTempVerId(ver.getId());
                    line.setFromNodeKey(from);
                    line.setToNodeKey(to);
                    if (e.has("priority")) {
                        line.setPriority(e.get("priority").asInt());
                    }
                    String cond = text(e, "cond");
                    line.setRemark(cond);
                    line.setStatus(1);
                    flowTempVerLineService.save(line);
                    saveLineCondFromEdge(line.getId(), cond, 0L);
                }
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(400, "graphJson 导入失败");
        }
    }

    private Map<String, Object> toDesignerNode(FlowTempVerNode n, int idx) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", n.getId());
        m.put("nodeKey", n.getNodeKey());
        m.put("nodeType", n.getNodeType());
        m.put("nodeName", n.getNodeName());
        m.put("parentNodeKey", n.getParentNodeKey());
        m.put("sortNo", n.getSortNo());
        m.put("configJson", n.getConfigJson());
        double[] xy = readLayout(n.getConfigJson(), idx);
        m.put("x", xy[0]);
        m.put("y", xy[1]);
        return m;
    }

    private Map<String, Object> toDesignerEdge(FlowTempVerLine l, List<FlowTempVerLineCond> conds) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", l.getId());
        m.put("fromNodeKey", l.getFromNodeKey());
        m.put("toNodeKey", l.getToNodeKey());
        m.put("priority", l.getPriority());
        m.put("isDefault", l.getIsDefault());
        String cond = l.getRemark();
        if ((cond == null || cond.isBlank()) && conds != null && !conds.isEmpty()) {
            cond = formatCond(conds.get(0));
        }
        m.put("cond", cond);
        return m;
    }

    private String formatCond(FlowTempVerLineCond c) {
        if (c == null || c.getLeftVar() == null) {
            return null;
        }
        String op = c.getCmpOp() == null ? "EQ" : c.getCmpOp().trim().toUpperCase();
        String fn = switch (op) {
            case "NE" -> "ne";
            case "GT" -> "gt";
            case "GE" -> "ge";
            case "LT" -> "lt";
            case "LE" -> "le";
            case "IN" -> "in";
            case "EXISTS" -> "exists";
            default -> "eq";
        };
        if ("exists".equals(fn)) {
            return "exists(" + c.getLeftVar() + ")";
        }
        String rv = c.getRightValue() == null ? "" : c.getRightValue();
        if ("string".equalsIgnoreCase(c.getRightType()) && !rv.startsWith("\"")) {
            rv = "\"" + rv + "\"";
        }
        return fn + "(" + c.getLeftVar() + ", " + rv + ")";
    }

    private ParsedCond parseCondExpr(String cond) {
        if (cond.startsWith("exists(") && cond.endsWith(")")) {
            String v = cond.substring(7, cond.length() - 1).trim();
            return new ParsedCond(v, "EXISTS", "null", null);
        }
        int p = cond.indexOf('(');
        int q = cond.lastIndexOf(')');
        if (p <= 0 || q <= p) {
            return null;
        }
        String fn = cond.substring(0, p).trim().toLowerCase();
        String inner = cond.substring(p + 1, q).trim();
        String cmp = switch (fn) {
            case "ne" -> "NE";
            case "gt" -> "GT";
            case "ge" -> "GE";
            case "lt" -> "LT";
            case "le" -> "LE";
            case "in" -> "IN";
            default -> "EQ";
        };
        String[] parts = inner.split(",", 2);
        if (parts.length == 0) {
            return null;
        }
        String left = parts[0].trim();
        String right = parts.length > 1 ? parts[1].trim() : "";
        String rt = "string";
        if (right.matches("-?\\d+(\\.\\d+)?")) {
            rt = "number";
        } else if ("true".equalsIgnoreCase(right) || "false".equalsIgnoreCase(right)) {
            rt = "bool";
        } else if (right.startsWith("\"") && right.endsWith("\"")) {
            right = right.substring(1, right.length() - 1);
        }
        return new ParsedCond(left, cmp, rt, right);
    }

    private String mergeLayoutConfig(DesignerNode dn) {
        try {
            ObjectNode o = objectMapper.createObjectNode();
            if (dn.configJson() != null && !dn.configJson().isBlank()) {
                JsonNode existing = objectMapper.readTree(dn.configJson());
                if (existing.isObject()) {
                    o = (ObjectNode) existing.deepCopy();
                }
            }
            if (dn.x() != null) {
                o.put("x", dn.x());
            }
            if (dn.y() != null) {
                o.put("y", dn.y());
            }
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return defaultLayoutJson(0);
        }
    }

    private double[] readLayout(String configJson, int idx) {
        try {
            if (configJson != null && !configJson.isBlank()) {
                JsonNode n = objectMapper.readTree(configJson);
                if (n.has("x") && n.has("y")) {
                    return new double[] { n.get("x").asDouble(), n.get("y").asDouble() };
                }
            }
        } catch (Exception ignored) {
        }
        int col = idx % 4;
        int row = idx / 4;
        return new double[] { 80 + col * 180, 80 + row * 100 };
    }

    private String defaultLayoutJson(int idx) {
        try {
            int col = idx % 4;
            int row = idx / 4;
            ObjectNode o = objectMapper.createObjectNode();
            o.put("x", 80 + col * 180);
            o.put("y", 80 + row * 100);
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }

    private JsonNode parseConfigJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    private String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        return v.asText();
    }

    private Map<Long, List<FlowTempVerLineCond>> loadCondsByLines(List<FlowTempVerLine> lines) {
        if (lines.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = lines.stream().map(FlowTempVerLine::getId).filter(Objects::nonNull).collect(Collectors.toList());
        List<FlowTempVerLineCond> all = flowTempVerLineCondService.lambdaQuery().in(FlowTempVerLineCond::getLineId, ids).list();
        Map<Long, List<FlowTempVerLineCond>> map = new HashMap<>();
        for (FlowTempVerLineCond c : all) {
            map.computeIfAbsent(c.getLineId(), k -> new ArrayList<>()).add(c);
        }
        return map;
    }

    private List<FlowTempVerNode> listNodes(Long tempVerId, long systemId, long tenantId) {
        return flowTempVerNodeService.lambdaQuery()
                .eq(FlowTempVerNode::getSystemId, systemId)
                .eq(FlowTempVerNode::getTenantId, tenantId)
                .eq(FlowTempVerNode::getTempVerId, tempVerId)
                .orderByAsc(FlowTempVerNode::getSortNo)
                .list();
    }

    private List<FlowTempVerLine> listLines(Long tempVerId, long systemId, long tenantId) {
        return flowTempVerLineService.lambdaQuery()
                .eq(FlowTempVerLine::getSystemId, systemId)
                .eq(FlowTempVerLine::getTenantId, tenantId)
                .eq(FlowTempVerLine::getTempVerId, tempVerId)
                .orderByAsc(FlowTempVerLine::getPriority)
                .list();
    }

    private FlowTempVer requireVer(Long tempVerId, long systemId, long tenantId) {
        FlowTempVer ver = flowTempVerService.getById(tempVerId);
        if (ver == null) {
            throw new BusinessException(404, "版本不存在");
        }
        if (!Objects.equals(ver.getSystemId(), systemId) || !Objects.equals(ver.getTenantId(), tenantId)) {
            throw new BusinessException(403, "无权访问该版本");
        }
        return ver;
    }

    public record DesignerNode(
            Long id,
            String nodeKey,
            String nodeType,
            String nodeName,
            String parentNodeKey,
            Integer sortNo,
            Double x,
            Double y,
            String configJson
    ) {}

    public record DesignerEdge(
            Long id,
            String fromNodeKey,
            String toNodeKey,
            Integer priority,
            Integer isDefault,
            String cond
    ) {}

    public record SaveGraphBody(List<DesignerNode> nodes, List<DesignerEdge> edges, String graphConfigJson) {}

    private record ParsedCond(String leftVar, String cmpOp, String rightType, String rightValue) {}
}
