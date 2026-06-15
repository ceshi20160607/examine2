package com.unique.examine.module.manage.service;

import java.util.List;

import com.unique.examine.module.manage.bo.DictDeleteBO;
import com.unique.examine.module.manage.bo.DictItemSaveBO;
import com.unique.examine.module.manage.bo.DictItemUpdateBO;
import com.unique.examine.module.manage.bo.DictStatusBO;
import com.unique.examine.module.manage.bo.DictTypeSaveBO;
import com.unique.examine.module.manage.bo.DictTypeUpdateBO;
import com.unique.examine.module.manage.vo.DictItemVO;
import com.unique.examine.module.manage.vo.DictTypeVO;
import com.unique.examine.module.manage.vo.DictUsageVO;

/**
 * 系统字典管理服务。
 */
public interface DictManageService {

    /**
     * 查询字典类型。
     *
     * @param systemId 系统 ID
     * @param scopeType 作用域
     * @param tenantId 租户 ID
     * @param keyword 关键字
     * @param status 状态
     * @return 字典类型列表
     */
    List<DictTypeVO> listTypes(Long systemId, String scopeType, String tenantId, String keyword, String status);

    /**
     * 创建字典类型。
     *
     * @param systemId 系统 ID
     * @param saveBO 保存入参
     * @return 字典类型
     */
    DictTypeVO createType(Long systemId, DictTypeSaveBO saveBO);

    /**
     * 更新字典类型。
     *
     * @param systemId 系统 ID
     * @param dictTypeId 字典类型 ID
     * @param updateBO 更新入参
     * @return 字典类型
     */
    DictTypeVO updateType(Long systemId, Long dictTypeId, DictTypeUpdateBO updateBO);

    /**
     * 变更字典类型状态。
     *
     * @param systemId 系统 ID
     * @param dictTypeId 字典类型 ID
     * @param statusBO 状态入参
     * @return 字典类型
     */
    DictTypeVO changeTypeStatus(Long systemId, Long dictTypeId, DictStatusBO statusBO);

    /**
     * 查询字典项。
     *
     * @param systemId 系统 ID
     * @param dictTypeId 字典类型 ID
     * @param parentId 父项 ID
     * @param keyword 关键字
     * @param status 状态
     * @param treeMode 是否树模式
     * @return 字典项列表
     */
    List<DictItemVO> listItems(Long systemId, Long dictTypeId, String parentId, String keyword, String status,
            boolean treeMode);

    /**
     * 创建字典项。
     *
     * @param systemId 系统 ID
     * @param dictTypeId 字典类型 ID
     * @param saveBO 保存入参
     * @return 字典项
     */
    DictItemVO createItem(Long systemId, Long dictTypeId, DictItemSaveBO saveBO);

    /**
     * 更新字典项。
     *
     * @param systemId 系统 ID
     * @param dictItemId 字典项 ID
     * @param updateBO 更新入参
     * @return 字典项
     */
    DictItemVO updateItem(Long systemId, Long dictItemId, DictItemUpdateBO updateBO);

    /**
     * 变更字典项状态。
     *
     * @param systemId 系统 ID
     * @param dictItemId 字典项 ID
     * @param statusBO 状态入参
     * @return 字典项
     */
    DictItemVO changeItemStatus(Long systemId, Long dictItemId, DictStatusBO statusBO);

    /**
     * 查询字典使用情况。
     *
     * @param systemId 系统 ID
     * @param dictTypeId 字典类型 ID
     * @return 使用情况
     */
    DictUsageVO usages(Long systemId, Long dictTypeId);

    /**
     * 删除字典类型。
     *
     * @param systemId 系统 ID
     * @param dictTypeId 字典类型 ID
     * @param deleteBO 删除入参
     * @return 字典类型
     */
    DictTypeVO deleteType(Long systemId, Long dictTypeId, DictDeleteBO deleteBO);

    /**
     * 删除字典项。
     *
     * @param systemId 系统 ID
     * @param dictItemId 字典项 ID
     * @param deleteBO 删除入参
     * @return 字典项
     */
    DictItemVO deleteItem(Long systemId, Long dictItemId, DictDeleteBO deleteBO);
}
