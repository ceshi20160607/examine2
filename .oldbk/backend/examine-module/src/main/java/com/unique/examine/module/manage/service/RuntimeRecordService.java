package com.unique.examine.module.manage.service;

import java.util.List;

import com.unique.examine.core.common.response.PageResult;
import com.unique.examine.module.manage.bo.RecordQueryBO;
import com.unique.examine.module.manage.bo.RecordSaveBO;
import com.unique.examine.module.manage.bo.RecordSubmitBO;
import com.unique.examine.module.manage.bo.RecordUpdateBO;
import com.unique.examine.module.manage.vo.RecordDetailVO;
import com.unique.examine.module.manage.vo.RecordHistoryVO;
import com.unique.examine.module.manage.vo.RecordListItemVO;
import com.unique.examine.module.manage.vo.RecordMutationResultVO;
import com.unique.examine.module.manage.vo.RecordRelationVO;
import com.unique.examine.module.manage.vo.RuntimeMenuVO;
import com.unique.examine.module.manage.vo.RuntimeModuleSchemaVO;

/**
 * 运行台记录服务。
 */
public interface RuntimeRecordService {

    /**
     * 查询运行台菜单。
     *
     * @param systemId 系统 ID
     * @return 运行菜单树
     */
    List<RuntimeMenuVO> runtimeMenus(Long systemId);

    /**
     * 查询运行态模块 schema。
     *
     * @param systemId 系统 ID
     * @param moduleId 模块 ID
     * @return 运行态 schema
     */
    RuntimeModuleSchemaVO moduleSchema(Long systemId, Long moduleId);

    /**
     * 查询运行记录列表。
     *
     * @param systemId 系统 ID
     * @param moduleId 模块 ID
     * @param queryBO 查询入参
     * @return 记录分页
     */
    PageResult<RecordListItemVO> queryRecords(Long systemId, Long moduleId, RecordQueryBO queryBO);

    /**
     * 创建运行记录。
     *
     * @param systemId 系统 ID
     * @param moduleId 模块 ID
     * @param saveBO 保存入参
     * @return 写操作结果
     */
    RecordMutationResultVO createRecord(Long systemId, Long moduleId, RecordSaveBO saveBO);

    /**
     * 查询运行记录详情。
     *
     * @param systemId 系统 ID
     * @param moduleId 模块 ID
     * @param recordId 记录 ID
     * @return 记录详情
     */
    RecordDetailVO recordDetail(Long systemId, Long moduleId, Long recordId);

    /**
     * 更新运行记录。
     *
     * @param systemId 系统 ID
     * @param moduleId 模块 ID
     * @param recordId 记录 ID
     * @param updateBO 更新入参
     * @return 写操作结果
     */
    RecordMutationResultVO updateRecord(Long systemId, Long moduleId, Long recordId, RecordUpdateBO updateBO);

    /**
     * 软删除运行记录。
     *
     * @param systemId 系统 ID
     * @param moduleId 模块 ID
     * @param recordId 记录 ID
     * @return 写操作结果
     */
    RecordMutationResultVO deleteRecord(Long systemId, Long moduleId, Long recordId);

    /**
     * 提交运行记录。
     *
     * @param systemId 系统 ID
     * @param moduleId 模块 ID
     * @param recordId 记录 ID
     * @param submitBO 提交入参
     * @return 写操作结果
     */
    RecordMutationResultVO submitRecord(Long systemId, Long moduleId, Long recordId, RecordSubmitBO submitBO);

    /**
     * 查询运行记录历史。
     *
     * @param systemId 系统 ID
     * @param moduleId 模块 ID
     * @param recordId 记录 ID
     * @return 历史列表
     */
    List<RecordHistoryVO> recordHistory(Long systemId, Long moduleId, Long recordId);

    /**
     * 查询运行记录关联关系。
     *
     * @param systemId 系统 ID
     * @param moduleId 模块 ID
     * @param recordId 记录 ID
     * @return 关联关系列表
     */
    List<RecordRelationVO> recordRelations(Long systemId, Long moduleId, Long recordId);
}
