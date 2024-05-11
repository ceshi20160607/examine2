package com.unique.examine.handler;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjectUtil;
import com.unique.examine.entity.dto.ExamineBO;
import com.unique.examine.entity.dto.ExamineContext;
import com.unique.examine.entity.dto.ExamineFillParams;
import com.unique.examine.entity.dto.ExamineRecordParams;
import com.unique.examine.entity.po.*;
import com.unique.examine.enums.CheckStatusEnum;
import com.unique.examine.enums.ExamineFlagEnum;
import com.unique.examine.enums.ExamineNodeTypeEnum;
import com.unique.examine.enums.ExamineTypeEnum;
import com.unique.core.utils.BaseUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class CreateHandler extends AbstractHandler{

    @Override
    public ExamineNodeTypeEnum examineNodeTypeEnum() {
        return ExamineNodeTypeEnum.CREATE;
    }

    /** 创建
     * @param context
     */
    @Override
    public void build(ExamineContext context) {
        //0.更新
        //1.获取审批信息
        Examine examine = context.getExamine();
        //2.当前审批实例信息
        ExamineRecordParams examineOtherParams = context.getExamineRecordParams();
        //3.
        //4.创建审批记录
        ExamineRecord examineRecord = BeanUtil.copyProperties(examine, ExamineRecord.class);
        Long recordId = BaseUtil.getNextId();
        examineRecord.setId(recordId);
        examineRecord.setExamineId(examine.getId());
        examineRecord.setRelationId(examineOtherParams.getRelationId());
        examineRecord.setCreateUserId(examineOtherParams.getCreateUserId());
        examineRecord.setCreateTime(LocalDateTime.now());
        examineRecord.setUpdateTime(LocalDateTime.now());
        context.setExamineRecord(examineRecord);
        context.setExamineRecordId(recordId);

        //5.创建初始数据
        List<ExamineRecordNode> examineRecordNodes = new ArrayList<>();
        List<ExamineNode> examineNodes = context.getExamineNodeListMap().get(0L);
        ExamineNode node = examineNodes.get(0);
        ExamineRecordNode recordLog = BeanUtil.copyProperties(node, ExamineRecordNode.class);
        Long nodeRecordId = BaseUtil.getNextId();
        recordLog.setId(nodeRecordId);
        recordLog.setRecordId(recordId);
        examineRecordNodes.add(recordLog);
        //6.下一个处理人
        Long nodeAfterId = node.getId();
        //8.最后的数据
        context.setExamineRecordNodeUpdateList(examineRecordNodes);
        //9.如果要进行下一步需要处理
        List<ExamineNode> afterNodes = context.getExamineNodeListMap().get(nodeAfterId);
        if (CollectionUtil.isNotEmpty(afterNodes)) {
            afterNodes.forEach(f->{
                context.setExamineNodeId(f.getId());
                ExamineNodeTypeEnum nodeTypeEnum = ExamineNodeTypeEnum.parse(f.getNodeType());
                if (ObjectUtil.isNotEmpty(nodeTypeEnum)) {
                    AbstractHandler nextHandler = handlerService.getHandlerService(nodeTypeEnum);
                    //8.执行下一个处理人
                    nextHandler.build(context);
                }
            });
        }
    }

    /** 执行逻辑
     * @param context
     */
    @Override
    public void handle(ExamineContext context) {

    }
}
