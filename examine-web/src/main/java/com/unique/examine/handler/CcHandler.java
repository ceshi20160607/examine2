package com.unique.examine.handler;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjectUtil;
import com.unique.examine.entity.dto.ExamineContext;
import com.unique.examine.entity.po.ExamineNode;
import com.unique.examine.entity.po.ExamineRecordNode;
import com.unique.examine.entity.po.ExamineRecordNodeUser;
import com.unique.examine.enums.CheckStatusEnum;
import com.unique.examine.enums.ExamineNodeTypeEnum;
import com.unique.examine.enums.ExamineTypeEnum;
import com.unique.core.entity.base.bo.SendEmailBO;
import com.unique.core.utils.BaseUtil;
import com.unique.core.utils.EmailUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class CcHandler extends AbstractHandler{

    @Override
    public ExamineNodeTypeEnum examineNodeTypeEnum() {
        return ExamineNodeTypeEnum.CC;
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

        //5.构建node
        for (ExamineNode r : examineNodes) {
            //5.0基础数据
            Long nodeAfterId = r.getId();
            Integer status = CheckStatusEnum.CHECK_ING.getType();
            ExamineTypeEnum examineTypeEnum = ExamineTypeEnum.parse(r.getExamineType());
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
            //7.如果要进行下一步需要处理
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
        List<ExamineRecordNode> updateRecordNodes = new ArrayList<>();
        List<ExamineRecordNodeUser> updateRecordNodeUsers = new ArrayList<>();
        for (ExamineRecordNode r : nodes) {
            //0.获取用户
            List<ExamineRecordNodeUser> examineRecordNodeUsers = context.getExamineRecordNodeUserListMap().get(r.getNodeId());
            for (ExamineRecordNodeUser e : examineRecordNodeUsers) {
                String[] split = e.getEamil().split(",");
                SendEmailBO sendEmailBO = new SendEmailBO();
                sendEmailBO.setEmails(split);
                EmailUtil.sendEmailProcess(sendEmailBO);
                e.setStatus(CheckStatusEnum.CHECK_PASS.getType());
                updateRecordNodeUsers.add(e);
            }
            r.setStatus(CheckStatusEnum.CHECK_PASS.getType());
            updateRecordNodes.add(r);
        }
        //5.要更新数据
        context.getExamineRecordNodeUpdateList().addAll(updateRecordNodes);
        context.getExamineRecordNodeUserUpdateList().addAll(updateRecordNodeUsers);
//        //6.设置下一个处理人
//        List<ExamineRecordNode> afterNodes = context.getExamineRecordNodeListMap().get(nodeAfterId);
//        if (CollectionUtil.isNotEmpty(afterNodes)) {
//            //8.执行下一个处理人
//            AbstractHandler nextHandler = handlerService.getHandlerService(ExamineNodeTypeEnum.parse(afterNodes.get(0).getNodeType()));
//            nextHandler.handle(context);
//        }
    }
}
