package com.unique.examine.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.stream.CollectorUtil;
import cn.hutool.core.util.ObjectUtil;
import com.unique.examine.entity.bo.ExamineNodeBO;
import com.unique.examine.entity.po.ExamineNode;
import com.unique.examine.entity.po.ExamineRecordNode;
import com.unique.core.utils.BaseUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ApproveUtil {

    public static List<ExamineNode> recursionBuildHadSubList(List<ExamineNodeBO> list, Long id){
        List<ExamineNode> ret = new ArrayList<>();
        for (ExamineNodeBO t : list) {
            ExamineNode node = BeanUtil.copyProperties(t,ExamineNode.class);
            Long nextId = BaseUtil.getNextId();
            node.setId(nextId);
            node.setNodeBeforeId(id);
            ret.add(node);
            if (ObjectUtil.isNotEmpty(node.getNodeUserList())) {
                node.getNodeUserList().forEach(e -> {
                    e.setId(BaseUtil.getNextId());
                    e.setNodeId(nextId);
                });
            }
            if (ObjectUtil.isNotEmpty(t.getSubNodeList())) {
                ret.addAll(recursionBuildHadSubList(t.getSubNodeList(), nextId));
            }
        }
        return ret;
    }

    public static ExamineRecordNode recursionBuildRecordList(List<ExamineRecordNode> list){
        ExamineRecordNode ret = new ExamineRecordNode();
        Map<Long, List<ExamineRecordNode>> nodeListMap = list.stream().collect(Collectors.groupingBy(ExamineRecordNode::getNodeBeforeId));
        for (ExamineRecordNode t : list) {
            if (ObjectUtil.isNotEmpty(nodeListMap.get(t.getId()))) {
                t.setSubList(nodeListMap.get(t.getId()));
            }
        }
        List<ExamineRecordNode> retList = list.stream().filter(r -> r.getNodeBeforeId().equals(0L)).collect(Collectors.toList());
        if (CollectionUtil.isNotEmpty(retList)) {
            ret = retList.get(0);
        }
        return ret;
    }
}
