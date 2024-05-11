package com.unique.examine.handler;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjectUtil;
import com.unique.examine.entity.dto.ExamineBO;
import com.unique.examine.entity.dto.ExamineContext;
import com.unique.examine.entity.dto.ExamineFillParams;
import com.unique.examine.entity.po.ExamineNode;
import com.unique.examine.entity.po.ExamineNodeUser;
import com.unique.examine.entity.po.ExamineRecordNode;
import com.unique.examine.entity.po.ExamineRecordNodeUser;
import com.unique.examine.enums.*;
import com.unique.core.enums.SystemCodeEnum;
import com.unique.core.exception.BaseException;
import com.unique.core.utils.BaseUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;

@Component
public class BaseHandler extends AbstractHandler{

    @Override
    public ExamineNodeTypeEnum examineNodeTypeEnum() {
        return ExamineNodeTypeEnum.BASE;
    }

    /** 创建
     * @param context
     */
    @Override
    public void build(ExamineContext context) {
        //0.更新
        List<ExamineRecordNode> examineRecordNodes = context.getExamineRecordNodeUpdateList();
        List<ExamineRecordNodeUser> examineNodeUsers = context.getExamineRecordNodeUserUpdateList();
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
            Integer status = CheckStatusEnum.CHECK_ING.getType();
            ExamineTypeEnum examineTypeEnum = ExamineTypeEnum.parse(r.getExamineType());
            //5.1基本的log
            ExamineRecordNode recordLog1 = BeanUtil.copyProperties(r, ExamineRecordNode.class);
            Long nodeRecordId = BaseUtil.getNextId();
            recordLog1.setId(nodeRecordId);
            recordLog1.setRecordId(examineRecordId);
            recordLog1.setStatus(status);
            examineRecordNodes.add(recordLog1);

            //5.5人员的
            List<ExamineNodeUser> itemUsers = context.getExamineNodeUserListMap().get(nodeAfterId);
            List<ExamineRecordNodeUser> itemRecordUsers = new ArrayList<>();
            //审批人员  审批人类型 0 固定人员 1 固定人员上级 2角色 3发起人自选4
            switch (examineTypeEnum){
                case ROLE:
                case FIXED:
                    itemUsers.forEach(u->{
                        ExamineApplyTypeEnum applyTypeEnum = ExamineApplyTypeEnum.parse(u.getApplyType());
                        switch (applyTypeEnum){
                            case EMAIL:
                            case USER:
                                ExamineRecordNodeUser item = BeanUtil.copyProperties(u, ExamineRecordNodeUser.class);
                                item.setId(BaseUtil.getNextId());
                                item.setRecordId(examineRecordId);
                                item.setRecordNodeId(nodeRecordId);
                                itemRecordUsers.add(item);
                                break;
                            case DEPT:
                                List<Long> dUserIds = context.getDeptIdWithUserId().get(u.getDeptId());
                                if (CollectionUtil.isNotEmpty(dUserIds)){
                                    dUserIds.forEach(d->{
                                        ExamineRecordNodeUser d1 = BeanUtil.copyProperties(d, ExamineRecordNodeUser.class);
                                        d1.setId(BaseUtil.getNextId());
                                        d1.setRecordId(examineRecordId);
                                        d1.setRecordNodeId(nodeRecordId);
                                        d1.setUserId(d);
                                        itemRecordUsers.add(d1);
                                    });
                                }
                                break;
                            case ROLE:
                                List<Long> rUserIds = context.getRoleIdWithUserId().get(u.getDeptId());
                                if (CollectionUtil.isNotEmpty(rUserIds)){
                                    rUserIds.forEach(k->{
                                        ExamineRecordNodeUser d1 = BeanUtil.copyProperties(k, ExamineRecordNodeUser.class);
                                        d1.setId(BaseUtil.getNextId());
                                        d1.setRecordId(examineRecordId);
                                        d1.setRecordNodeId(nodeRecordId);
                                        d1.setUserId(k);
                                        itemRecordUsers.add(d1);
                                    });
                                }
                                break;
                        }
                    });
                    break;
                case FIXED_SUPER:
                    ExamineNodeUser itemfixedsuper = itemUsers.get(0);
                    List<Long> sUserIds = context.getSuperWithUserId().get(itemfixedsuper.getUserId());
                    if (CollectionUtil.isNotEmpty(sUserIds)){
                        sUserIds.forEach(d->{
                            ExamineRecordNodeUser d1 = BeanUtil.copyProperties(d, ExamineRecordNodeUser.class);
                            d1.setId(BaseUtil.getNextId());
                            d1.setRecordId(examineRecordId);
                            d1.setRecordNodeId(nodeRecordId);
                            d1.setUserId(d);
                            itemRecordUsers.add(d1);
                        });
                    }
                    break;
                case CHOOSE:
                    ExamineNodeUser itemfixedchoose = itemUsers.get(0);
                    ExamineRecordNodeUser d1 = BeanUtil.copyProperties(itemfixedchoose, ExamineRecordNodeUser.class);
                    d1.setId(BaseUtil.getNextId());
                    d1.setRecordId(examineRecordId);
                    d1.setRecordNodeId(nodeRecordId);
                    d1.setUserId(examineFillParams.get(0).getUserId());
                    itemRecordUsers.add(d1);
                    break;
            }

            examineNodeUsers.addAll(itemRecordUsers);
            //6.最后的数据
            context.setExamineRecordNodeUpdateList(examineRecordNodes);
            context.setExamineRecordNodeUserUpdateList(examineNodeUsers);
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

    /** 执行逻辑
     * @param context
     */
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

    /** 处理逻辑
     * @param nodes
     * @param context
     */
    private void baseProcess(List<ExamineRecordNode> nodes,ExamineContext context) {
        //0.审批数据
        ExamineBO examineBO = context.getExamineBO();
        Long examineUserId = examineBO.getUserId();
        List<ExamineRecordNode> updateRecordNodes = new ArrayList<>();
        List<ExamineRecordNodeUser> updateRecordNodeUsers = new ArrayList<>();
        //1.0循环处理
        for (ExamineRecordNode r : nodes) {
            ExamineFlagEnum examineFlagEnum = ExamineFlagEnum.parse(r.getExamineFlag());
            //0.获取用户
            List<ExamineRecordNodeUser> examineRecordNodeUsers = context.getExamineRecordNodeUserListMap().get(r.getNodeId());
            List<Integer> hadStatus = new ArrayList<>();
            for (ExamineRecordNodeUser u : examineRecordNodeUsers) {
                if (u.getStatus().equals(1)) {
                    hadStatus.add(1);
                    continue;
                }
                if (ExamineFlagEnum.DEFAULT.equals(r.getExamineFlag())) {
                    if (hadStatus.contains(0)) {
                        throw new BaseException(SystemCodeEnum.EXAMINE_NOT_ME_ERROR);
                    }
                }
                if (u.getUserId().equals(examineUserId)) {
                    u.setStatus(examineBO.getStatus());
                    u.setRemark(examineBO.getRemark());
                    updateRecordNodeUsers.add(u);
                    hadStatus.add(1);
                }else{
                    hadStatus.add(0);
                }
            }
            if (Arrays.asList(ExamineFlagEnum.DEFAULT.getType(), ExamineFlagEnum.MUST_ALL_NO_ORDER.getType()).equals(r.getExamineFlag())) {
                if (!hadStatus.contains(0)) {
                    //node更新
                    r.setStatus(CheckStatusEnum.CHECK_PASS.getType());
                    updateRecordNodes.add(r);
                }
            }
            switch (examineFlagEnum) {
                case DEFAULT:
                case MUST_ALL_NO_ORDER:
                    if (!hadStatus.contains(0)) {
                        //node更新
                        r.setStatus(CheckStatusEnum.CHECK_PASS.getType());
                        updateRecordNodes.add(r);
                    }
                    break;
                case ANY_ONE:
                    if (hadStatus.contains(1)) {
                        //node更新
                        r.setStatus(CheckStatusEnum.CHECK_PASS.getType());
                        updateRecordNodes.add(r);
                    }
                    break;
            }

        }
        //5.要更新数据
        context.getExamineRecordNodeUpdateList().addAll(updateRecordNodes);
        context.getExamineRecordNodeUserUpdateList().addAll(updateRecordNodeUsers);
//        if (gFlag) {
//            //6.设置下一个处理人
//            List<ExamineRecordNode> afterNodes = context.getExamineRecordNodeListMap().get(nodeAfterId);
//            if (CollectionUtil.isNotEmpty(afterNodes)) {
//                //7.要更新数据
//                context.getExamineRecordNodeUpdateList().addAll(nodes);
//                AbstractHandler nextHandler = handlerService.getHandlerService(ExamineNodeTypeEnum.parse(afterNodes.get(0).getNodeType()));
//                //8.执行下一个处理人
//                nextHandler.handle(context);
//            }
//        }
    }
}
