package com.unique.examine.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.unique.examine.entity.dto.ExamineContext;
import com.unique.examine.entity.po.*;
import com.unique.examine.entity.vo.ExamineRecordVO;
import com.unique.examine.enums.ExamineNodeTypeEnum;
import com.unique.examine.handler.AbstractHandler;
import com.unique.examine.handler.HandlerService;
import com.unique.examine.mapper.ExamineRecordMapper;
import com.unique.examine.service.*;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.utils.ApproveUtil;
import com.unique.module.manage.service.ModuleAuthManageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 审核记录表 服务实现类
 * </p>
 *
 * @author UNIQUE
 * @since 2024-04-02
 */
@Service
public class ExamineRecordServiceImpl extends ServiceImpl<ExamineRecordMapper, ExamineRecord> implements IExamineRecordService {

    @Autowired
    private ModuleAuthManageService moduleAuthManageService;

    @Autowired
    private HandlerService handlerService;

    @Autowired
    private IExamineService examineService;
    @Autowired
    private IExamineSettingService examineSettingService;
    @Autowired
    private IExamineSettingUserService examineSettingUserService;

    @Autowired
    private IExamineNodeService examineNodeService;
    @Autowired
    private IExamineNodeUserService examineNodeUserService;

    @Autowired
    private IExamineRecordNodeService examineRecordNodeService;
    @Autowired
    private IExamineRecordNodeUserService examineRecordNodeUserService;

    //---------------------------------查询业务------------------------------------

    @Override
    public ExamineRecordVO queryById(Long recordId) {
        ExamineRecordVO ret = new ExamineRecordVO();
        ExamineRecord record = getById(recordId);
        ret.setExamineRecord(record);

        //2.node
        List<ExamineRecordNode> nodeList = examineRecordNodeService.lambdaQuery().in(ExamineRecordNode::getRecordId, recordId).list();
        ExamineRecordNode nodes = ApproveUtil.recursionBuildRecordList(nodeList);
        ret.setExamineRecordNode(nodes);

        return ret;
    }

    //---------------------------------执行业务------------------------------------
    @Override
    @Transactional
    public void create(ExamineContext context) {

        fillBaseExamine(context,Boolean.FALSE);
//        //3.创建审批业务实例Record
        AbstractHandler createHandler = handlerService.getHandlerService(ExamineNodeTypeEnum.CREATE);
        createHandler.build(context);
        //4保存=》db
        save(context.getExamineRecord());
        examineRecordNodeService.saveOrUpdateBatch(context.getExamineRecordNodeUpdateList());
    }

    @Override
    @Transactional
    public void process(ExamineContext context) {

        fillBaseExamine(context,Boolean.TRUE);
        //2.获取当前要进行审批的node
        Long examineNodeId = context.getExamineNodeId();
        List<ExamineRecordNode> afterNodes = context.getExamineRecordNodeListMap().get(examineNodeId);
        if (CollectionUtil.isNotEmpty(afterNodes)) {
            AbstractHandler baseService = handlerService.getHandlerService(ExamineNodeTypeEnum.parse(afterNodes.get(0).getNodeType()));
            //3.执行下一个处理人
            baseService.handle(context);
        }
        //4.执行更新
        examineRecordNodeService.saveOrUpdateBatch(context.getExamineRecordNodeUpdateList());
    }

    //---------------------------------基础参数------------------------------------
    /**补全基础数据
     * @param context
     */
    private void fillBaseExamine(ExamineContext context ,Boolean recordFlag) {
        //构建基础数据

        context.setSuperWithUserId(moduleAuthManageService.querySuperUserGroupByUserId(context.getModuleId()));
        context.setDeptIdWithUserId(moduleAuthManageService.queryDeptUserIdGroupByDeptId(context.getModuleId()));
        context.setRoleIdWithUserId(moduleAuthManageService.queryRoleUserIdGroupByRoleId(context.getModuleId()));

        //0.前置环境

        //1.examine基础数据
        context.setExamine(examineService.getById(context.getExamineId()));
        //1.2.examineSetting基础数据
        List<ExamineSetting> settingList = examineSettingService.lambdaQuery().eq(ExamineSetting::getExamineId, context.getExamineId()).orderByAsc(ExamineSetting::getId).list();
        context.setExamineSetting(settingList.get(0));
        //1.3.examineSettingUser基础数据
        List<ExamineSettingUser> examineSettingUserList = examineSettingUserService.lambdaQuery().eq(ExamineSettingUser::getExamineId, context.getExamineId()).orderByAsc(ExamineSettingUser::getId).list();
        context.setExamineSettingUserList(examineSettingUserList);

        //1.4examineNode基础数据
        List<ExamineNode> examineNodeList = examineNodeService.lambdaQuery().eq(ExamineNode::getExamineId, context.getExamineId()).orderByAsc(ExamineNode::getId).list();
        Map<Long, List<ExamineNode>> examineNodeListMap = examineNodeList.stream().collect(Collectors.groupingBy(r -> r.getNodeBeforeId()));
        context.setExamineNodeListMap(examineNodeListMap);

        //1.5.examineNodeUser基础数据
        List<ExamineNodeUser> examineNodeUserList = examineNodeUserService.lambdaQuery().eq(ExamineNodeUser::getExamineId, context.getExamineId()).orderByAsc(ExamineNodeUser::getId).list();
        Map<Long, List<ExamineNodeUser>> examineNodeUserListMap = examineNodeUserList.stream().collect(Collectors.groupingBy(ExamineNodeUser::getNodeId, Collectors.collectingAndThen(Collectors.toList(), item -> {
            item.sort(Comparator.comparing(ExamineNodeUser::getId));
            return item;
        })));
        context.setExamineNodeUserListMap(examineNodeUserListMap);

        //2.补充数据--自选中审批人--废弃
        //2.2.动态添加修改审批节点的这里进行补充

        //3.审批实例数据
        if (recordFlag) {
            //3.1.examineRecord基础数据
            ExamineRecord examineRecord = getById(context.getExamineRecordId());
            context.setExamineRecord(examineRecord);
            //3.2.examineRecordNode基础数据
            List<ExamineRecordNode> examineRecordNodeList = examineRecordNodeService.lambdaQuery().eq(ExamineRecordNode::getRecordId, context.getExamineRecordId()).list();
            Map<Long, List<ExamineRecordNode>> examineRecordNodeListMap = examineRecordNodeList.stream().collect(Collectors.groupingBy(ExamineRecordNode::getNodeBeforeId));
            context.setExamineRecordNodeListMap(examineRecordNodeListMap);
            //3.3.examineRecordNodeUser基础数据
            List<ExamineRecordNodeUser> examineRecordNodeUserList = examineRecordNodeUserService.lambdaQuery().eq(ExamineRecordNodeUser::getRecordId, context.getExamineRecordId()).list();
            Map<Long, List<ExamineRecordNodeUser>> examineRecordNodeUserListMap = examineRecordNodeUserList.stream().collect(Collectors.groupingBy(ExamineRecordNodeUser::getNodeId));
            context.setExamineRecordNodeUserListMap(examineRecordNodeUserListMap);
        }
    }
}
