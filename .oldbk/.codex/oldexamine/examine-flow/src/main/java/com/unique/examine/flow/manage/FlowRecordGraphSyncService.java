package com.unique.examine.flow.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.flow.entity.po.FlowRecord;
import com.unique.examine.flow.entity.po.FlowRecordLine;
import com.unique.examine.flow.entity.po.FlowRecordNode;
import com.unique.examine.flow.service.IFlowRecordLineService;
import com.unique.examine.flow.service.IFlowRecordNodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 将 {@code FlowRecord.graph_json} 同步到关系表 {@code un_flow_record_node} / {@code un_flow_record_line}，
 * 便于查询与引擎优先走关系表；边上条件字符串暂存在 {@link FlowRecordLine#getRemark()} 的 JSON 中（{@code {"cond":"..."}}）。
 */
@Service
public class FlowRecordGraphSyncService {

    @Autowired
    private IFlowRecordNodeService flowRecordNodeService;
    @Autowired
    private IFlowRecordLineService flowRecordLineService;
    @Autowired
    private ObjectMapper objectMapper;

    public void syncFromGraphJson(FlowRecord rec, long platId, Long sourceTempVerId) {
        if (rec == null || rec.getGraphJson() == null || rec.getGraphJson().isBlank()) {
            return;
        }
        Long existedLine = flowRecordLineService.lambdaQuery()
                .eq(FlowRecordLine::getRecordId, rec.getId())
                .count();
        if (existedLine != null && existedLine > 0) {
            return;
        }
        Long existedNode = flowRecordNodeService.lambdaQuery()
                .eq(FlowRecordNode::getRecordId, rec.getId())
                .count();
        if (existedNode != null && existedNode > 0) {
            return;
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(rec.getGraphJson());
        } catch (Exception e) {
            return;
        }
        JsonNode nodes = root.path("nodes");
        JsonNode edges = root.path("edges");
        if (!nodes.isArray() || !edges.isArray()) {
            return;
        }

        int sort = 0;
        for (JsonNode n : nodes) {
            String nodeKey = n.path("id").asText(null);
            if (nodeKey == null || nodeKey.isBlank()) {
                continue;
            }
            FlowRecordNode rn = new FlowRecordNode();
            rn.setSystemId(rec.getSystemId());
            rn.setTenantId(rec.getTenantId());
            rn.setRecordId(rec.getId());
            rn.setNodeKey(nodeKey.trim());
            String parent = n.path("parentId").asText(null);
            if (parent == null || parent.isBlank()) {
                parent = n.path("parent_node_key").asText(null);
            }
            rn.setParentNodeKey(parent == null || parent.isBlank() ? null : parent.trim());
            rn.setNodeType(n.path("type").asText("custom"));
            rn.setNodeName(n.path("name").asText(null));
            rn.setSortNo(sort++);
            rn.setStatus(1);
            rn.setSourceTempVerId(sourceTempVerId);
            rn.setConfigJson(n.path("config").isMissingNode() ? null : n.path("config").toString());
            rn.setCreateUserId(platId);
            rn.setUpdateUserId(platId);
            flowRecordNodeService.save(rn);
        }

        int idx = 0;
        for (JsonNode e : edges) {
            String from = e.path("from").asText(null);
            String to = e.path("to").asText(null);
            if (from == null || from.isBlank() || to == null || to.isBlank()) {
                idx++;
                continue;
            }
            FlowRecordLine line = new FlowRecordLine();
            line.setSystemId(rec.getSystemId());
            line.setTenantId(rec.getTenantId());
            line.setRecordId(rec.getId());
            line.setFromNodeKey(from.trim());
            line.setToNodeKey(to.trim());
            Integer pr = e.has("priority") && e.get("priority").canConvertToInt() ? e.get("priority").asInt() : idx;
            line.setPriority(pr);
            line.setIsDefault(e.path("is_default").asInt(0));
            line.setStatus(1);
            String cond = e.has("cond") ? e.path("cond").asText(null) : null;
            if (cond != null && !cond.isBlank()) {
                try {
                    line.setRemark(objectMapper.writeValueAsString(java.util.Map.of("cond", cond.trim())));
                } catch (Exception ex) {
                    line.setRemark("{\"cond\":\"" + cond.replace("\"", "\\\"") + "\"}");
                }
            }
            line.setCreateUserId(platId);
            line.setUpdateUserId(platId);
            flowRecordLineService.save(line);
            idx++;
        }
    }
}
