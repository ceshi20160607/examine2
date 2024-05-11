package com.unique.examine.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjectUtil;
import com.unique.admin.service.IAdminUserRoleService;
import com.unique.admin.service.IAdminUserService;
import com.unique.examine.entity.bo.ExamineNodeBO;
import com.unique.examine.entity.dto.ExamineNodeAdd;
import com.unique.examine.entity.po.ExamineNodeUser;
import com.unique.examine.entity.po.ExamineRecordNode;
import com.unique.examine.entity.po.ExamineRecordNodeUser;
import com.unique.examine.enums.CheckStatusEnum;
import com.unique.examine.enums.ExamineApplyTypeEnum;
import com.unique.examine.enums.ExamineNodeTypeEnum;
import com.unique.examine.enums.ExamineTypeEnum;
import com.unique.examine.handler.AbstractHandler;
import com.unique.examine.mapper.ExamineRecordNodeMapper;
import com.unique.examine.service.IExamineRecordNodeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.service.IExamineRecordNodeUserService;
import com.unique.core.bo.SendEmailBO;
import com.unique.core.utils.BaseUtil;
import com.unique.core.utils.EmailUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 审批节点表 服务实现类
 * </p>
 *
 * @author UNIQUE
 * @since 2024-04-02
 */
@Service
public class ExamineRecordNodeServiceImpl extends ServiceImpl<ExamineRecordNodeMapper, ExamineRecordNode> implements IExamineRecordNodeService {

    @Autowired
    private IAdminUserService adminUserService;
    @Autowired
    private IAdminUserRoleService adminUserRoleService;

    @Autowired
    private IExamineRecordNodeUserService examineRecordNodeUserService;
    /**
     * 添加审批节点
     * 目前仅限单节点添加
     * @param nodeAdd
     */
    @Override
    public void addNewNode(ExamineNodeAdd nodeAdd) {
        ExamineNodeBO examineNode = nodeAdd.getExamineNode();
        if (ObjectUtil.isNotEmpty(examineNode)) {

            //-------------------------系统用户参数------------------------------
            //人员上级的用户
            Map<Long,List<Long>> superWithUserId = adminUserService.querySuperUserGroupByUserId();
            //部门的用户
            Map<Long,List<Long>> deptIdWithUserId = adminUserService.queryDeptUserIdGroupByRoleId();
            //角色下的用户
            Map<Long,List<Long>> roleIdWithUserId = adminUserRoleService.queryRoleUserIdGroupByRoleId();
            //-------------------------系统用户参数------------------------------

            List<ExamineRecordNodeUser> itemRecordUsers = new ArrayList<>();
            Long examineRecordId = nodeAdd.getExamineRecordId();
            List<ExamineNodeUser> itemUsers = examineNode.getNodeUserList();
            Integer status = CheckStatusEnum.CHECK_ING.getType();
            ExamineRecordNode recordNode = BeanUtil.copyProperties(examineNode, ExamineRecordNode.class);
            //2.0节点数据
            Long nodeRecordId = BaseUtil.getNextId();
            recordNode.setRecordId(examineRecordId);
            recordNode.setId(nodeRecordId);

            ExamineNodeTypeEnum nodeTypeEnum = ExamineNodeTypeEnum.parse(examineNode.getNodeType());
            ExamineTypeEnum examineTypeEnum = ExamineTypeEnum.parse(examineNode.getExamineType());

            //5.节点类型
            switch (nodeTypeEnum) {
                case CREATE:
                    break;
                case BASE:
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
                                        List<Long> dUserIds = deptIdWithUserId.get(u.getDeptId());
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
                                        List<Long> rUserIds = roleIdWithUserId.get(u.getDeptId());
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
                            List<Long> sUserIds = superWithUserId.get(itemfixedsuper.getUserId());
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
                            break;
                    }
                    break;
                case CONDITION:
                    break;
                case CC:
                    break;
                case TRANSFER:
                    break;
                case WITHIN_CONDITIONS:
                    break;
            }

            //7.0保存节点
            save(recordNode);
            //7.1保存用户
            if (CollectionUtil.isNotEmpty(itemRecordUsers)){
                examineRecordNodeUserService.saveBatch(itemRecordUsers);
            }

        }
    }
}
