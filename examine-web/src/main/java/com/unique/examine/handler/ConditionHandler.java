package com.unique.examine.handler;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson2.JSON;
import com.unique.examine.entity.dto.ExamineContext;
import com.unique.examine.entity.dto.ExamineFillParams;
import com.unique.examine.entity.dto.ExamineSearch;
import com.unique.examine.entity.po.ExamineNode;
import com.unique.examine.entity.po.ExamineNodeUser;
import com.unique.examine.entity.po.ExamineRecordNode;
import com.unique.examine.entity.po.ExamineRecordNodeUser;
import com.unique.examine.enums.CheckStatusEnum;
import com.unique.examine.enums.ExamineNodeTypeEnum;
import com.unique.examine.enums.ExamineTypeEnum;
import com.unique.core.utils.BaseUtil;
import com.unique.core.utils.SearchFieldUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ConditionHandler extends AbstractHandler{
    @Override
    public ExamineNodeTypeEnum examineNodeTypeEnum() {
        return ExamineNodeTypeEnum.CONDITION;
    }

    @Override
    public void build(ExamineContext context) {
        //0.更新
        List<ExamineRecordNode> examineRecordNodes = context.getExamineRecordNodeUpdateList();
        //1.本次节点
        Long examineNodeId = context.getExamineNodeId();
        //1.1实例的id
        Long examineRecordId = context.getExamineRecordId();
        List<ExamineNode> examineNodes = context.getExamineNodeListMap().get(examineNodeId);
        //3.自选的数据
        Map<Long, List<ExamineFillParams>> examineFillParamsListMap = new HashMap<>();
        List<ExamineFillParams> examineFillParams = examineFillParamsListMap.get(examineNodeId);

        //5.构建node
        for (ExamineNode r : examineNodes) {
            //5.0基础数据
            Long nodeAfterId = r.getId();
            Integer status = CheckStatusEnum.CHECK_PASS.getType();
            //5.1基本的log
            ExamineRecordNode recordLog1 = BeanUtil.copyProperties(r, ExamineRecordNode.class);
            Long nodeRecordId = BaseUtil.getNextId();
            recordLog1.setId(nodeRecordId);
            recordLog1.setRecordId(examineRecordId);
            recordLog1.setStatus(status);
            examineRecordNodes.add(recordLog1);
            //6.最后的数据
            context.setExamineRecordNodeUpdateList(examineRecordNodes);
            //6.1
            context.setExamineNodeId(nodeAfterId);
            //9.如果要进行下一步需要处理
            List<ExamineNode> afterNodes = context.getExamineNodeListMap().get(nodeAfterId);
            if (CollectionUtil.isNotEmpty(afterNodes)) {
                afterNodes.forEach(f->{
                    ExamineNodeTypeEnum nodeTypeEnum = ExamineNodeTypeEnum.parse(f.getNodeType());
                    if (ObjectUtil.isNotEmpty(nodeTypeEnum)) {
                        AbstractHandler nextHandler = handlerService.getHandlerService(nodeTypeEnum);
                        //8.执行下一个处理人
                        nextHandler.build(context);
                    }
                });
            }
        }
    }

    @Override
    public void handle(ExamineContext context) {
        //1.校验
        Long examineNodeId = context.getExamineNodeId();
        List<ExamineRecordNode> examineRecordNodes = context.getExamineRecordNodeListMap().get(examineNodeId);
        if (CollectionUtil.isNotEmpty(examineRecordNodes)) {
            //2.审核操作
            baseProcess(examineRecordNodes, context);
        }
    }

    private void baseProcess(List<ExamineRecordNode> nodes, ExamineContext context) {
        Map<String, Object> entity = context.getExamineRecordParams().getEntity();
        Boolean itemRet = Boolean.TRUE;
        for (ExamineRecordNode r : nodes) {
            if (!itemRet) {
                r.setStatus(CheckStatusEnum.CHECK_DISCARD.getType());
            }
            List<ExamineSearch> examineSearcheList = JSON.parseArray(r.getConditionModuleFieldSearch(), ExamineSearch.class);
            for (ExamineSearch search : examineSearcheList) {
                itemRet = itemRet && SearchFieldUtil.searchConditionValue(search, entity);
            }
            if (itemRet) {
                r.setStatus(CheckStatusEnum.CHECK_PASS.getType());
                //5.要更新数据
                context.getExamineRecordNodeUpdateList().addAll(nodes);

                Long nodeAfterId = nodes.get(0).getId();
                //6.设置下一级id
                context.setExamineNodeId(nodeAfterId);

                List<ExamineRecordNode> afterNodes = context.getExamineRecordNodeListMap().get(nodeAfterId);
                if (CollectionUtil.isNotEmpty(afterNodes)) {
                    afterNodes.forEach(f->{
                        ExamineNodeTypeEnum nodeTypeEnum = ExamineNodeTypeEnum.parse(f.getNodeType());
                        if (ObjectUtil.isNotEmpty(nodeTypeEnum)) {
                            AbstractHandler nextHandler = handlerService.getHandlerService(nodeTypeEnum);
                            //8.执行下一个处理人
                            nextHandler.build(context);
                        }
                    });
                }
            }
        }
    }
}
