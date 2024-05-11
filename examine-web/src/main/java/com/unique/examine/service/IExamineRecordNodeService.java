package com.unique.examine.service;

import com.unique.examine.entity.dto.ExamineNodeAdd;
import com.unique.examine.entity.dto.ExamineNodeFill;
import com.unique.examine.entity.po.ExamineRecordNode;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 审批节点表 服务类
 * </p>
 *
 * @author UNIQUE
 * @since 2024-04-02
 */
public interface IExamineRecordNodeService extends IService<ExamineRecordNode> {

    void addNewNode(ExamineNodeAdd nodeAdd);
}
