package ${package.ServiceImpl};

import ${package.Entity}.${entity};
import ${package.Mapper}.${table.mapperName};
import ${package.Service}.${table.serviceName};
import ${superServiceImplClassPackage};

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.util.TypeUtils;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kakarote.common.field.entity.FieldData;
import com.kakarote.common.field.service.FieldDataService;
import com.kakarote.common.log.entity.OperationLog;
import com.kakarote.common.log.enums.OperateObjectEnum;
import com.kakarote.core.common.enums.FieldEnum;
import com.kakarote.core.exception.CrmException;
import com.kakarote.core.feign.admin.entity.AdminConfig;
import com.kakarote.core.feign.admin.entity.SimpleUser;
import com.kakarote.core.servlet.ApplicationContextHolder;
import com.kakarote.core.utils.ExcelParseUtil;
import com.kakarote.core.utils.UserCacheUtil;
import com.kakarote.crm.common.ActionRecordUtil;
import com.kakarote.crm.common.CrmModel;
import com.kakarote.crm.common.LanguageFieldUtil;
import com.kakarote.crm.common.RecordValueParseUtil;
import com.kakarote.crm.constant.CrmActivityEnum;
import com.kakarote.crm.constant.CrmCodeEnum;
import com.kakarote.crm.constant.CrmEnum;
import com.kakarote.crm.entity.BO.*;
import com.kakarote.crm.entity.PO.*;
import com.kakarote.crm.entity.VO.CrmFieldSortVO;
import com.kakarote.crm.entity.VO.CrmModelFieldVO;
import com.kakarote.core.entity.BasePage;
import com.kakarote.crm.service.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>
 * ${table.comment!} 服务实现类
 * </p>
 *
 * @author ${author}
 * @since ${date}
 */
@Service
<#if kotlin>
open class ${table.serviceImplName} : ${superServiceImplClass}<${table.mapperName}, ${entity}>(), ${table.serviceName} {

}
<#else>
public class ${table.serviceImplName} extends ${superServiceImplClass}<${table.mapperName}, ${entity}> implements ${table.serviceName},CrmPageService {


    @Autowired
    private ICrmActivityService crmActivityService;
    @Autowired
    private ICrmActionRecordService crmActionRecordService;
    @Autowired
    private ActionRecordUtil actionRecordUtil;
    @Autowired
    private ICrmFieldService crmFieldService;

    @Autowired
    private FieldDataService fieldDataService;

    @Override
    public String[] appendSearch() {
        return new String[]{"num"};
    }

    @Override
    public void setOtherField(Map<String, Object> map) {
        setCommonField(map);
    }

    @Override
    public CrmEnum getLabel() {
        return CrmEnum.CUSTOMER;
    }
    /**
    * 导出时查询所有数据
    *
    * @param search 业务查询对象
    * @return data
    */
    @Override
    public BasePage<Map<String, Object>> queryPageList(CrmSearchBO search) {
        BasePage<Map<String, Object>> basePage = queryList(search);
        return basePage;
    }

    /**
    * 查询字段配置
    *
    * @param id 主键ID
    * @return data
    */
    @Override
    public List<CrmModelFieldVO> queryField(Long id) {
        CrmModel baseModel = queryById(id, true);
        List<CrmModelFieldVO> vos = crmFieldService.queryField(baseModel, true);
        return vos;
    }
    private List<CrmModelFieldVO> queryField(Long id, boolean appendInformation) {
        CrmModel crmModel = queryById(id, false);

        List<CrmModelFieldVO> vos = crmFieldService.queryField(crmModel, true);
        //        JSONObject value = new JSONObject();
        //        value.put("location", crmModel.get("location"));
        //        value.put("address", crmModel.get("address"));
        //        value.put("detailAddress", crmModel.get("detailAddress"));
        //        value.put("lng", crmModel.get("lng"));
        //        value.put("lat", crmModel.get("lat"));
        //        Map<String, String> keyMap = new HashMap<>();
        //        keyMap.put("name_resourceKey", "customField.customer.mapAddress");
        //        vos.add(new CrmModelFieldVO("map_address", FieldEnum.MAP_ADDRESS, "地区定位", 1).setFormPosition("99,0").setIsNull(0).setValue(value).setLanguageKeyMap(keyMap));
        if (appendInformation) {
            List<CrmModelFieldVO> modelFiledVOS = appendInformation(crmModel);
            String preOwnerUserId = "preOwnerUserId";
            String ownerUserId = "ownerUserId";
            if (crmModel.get(preOwnerUserId) != null && crmModel.get(ownerUserId) == null) {
                CrmModelFieldVO filedVO = new CrmModelFieldVO("preOwnerUserName", FieldEnum.SINGLE_USER, "前负责人", 1);
                List<SimpleUser> data = UserCacheUtil.getSimpleUsers(Collections.singleton((Long) crmModel.get("preOwnerUserId")));
                filedVO.setValue(data.get(0));
                modelFiledVOS.add(filedVO.setSysInformation(1));
            }
            String ownerDeptName = UserCacheUtil.getDeptName(UserCacheUtil.getSimpleUser((Long) crmModel.get("ownerUserId")).getDeptId());
            modelFiledVOS.add(new CrmModelFieldVO("receive_time", FieldEnum.DATETIME, "负责人获取客户时间", 1).setValue(crmModel.get("receiveTime")).setSysInformation(1));
            modelFiledVOS.add(new CrmModelFieldVO("ownerDeptName", FieldEnum.TEXT, "所属部门", 1).setValue(ownerDeptName).setSysInformation(1));
            for (CrmModelFieldVO filedVO : modelFiledVOS) {
                //添加语言包key
                filedVO.setLanguageKeyMap(LanguageFieldUtil.getFieldNameKeyMap("name_resourceKey", filedVO.getFieldName(), filedVO.getSetting(), crmModel.getLabel()));
            }
            vos.addAll(modelFiledVOS);
        }
        return vos;
    }
    /**
    * 查询字段配置
    *
    * @param id 主键ID
    * @return data
    */
    @Override
    public List<List<CrmModelFieldVO>> queryFormPositionField(Long id) {
        List<CrmModelFieldVO> fieldList = queryField(id, false);
        List<List<CrmModelFieldVO>> vos = crmFieldService.queryFormPositionField(fieldList, getLabel(), id != null);

        for (List<CrmModelFieldVO> filedVOList : vos) {
            filedVOList.forEach(field -> {
                if ("ownerUserId".equals(field.getFieldName())) {
                    SimpleUser user = new SimpleUser();
                    user.setUserId(UserUtil.getUserId());
                    user.setRealname(UserUtil.getUser().getRealname());
                    field.setDefaultValue(Collections.singleton(user));
                }
            });
        }
        return vos;
    }
    /**
    * 保存或新增信息
    */
    @Override
    public Map<String, Object> addOrUpdate(CrmBusinessSaveBO crmModel, boolean isExcel) {
        Map<String, Object> map = new HashMap<>();
        List<OperationLog> operationLogList = new ArrayList<>();
        OperationLog operationLog = new OperationLog();

        setData(crmModel.getEntity());
        ${entity} newModel = BeanUtil.copyProperties(crmModel.getEntity(), ${entity}.class);

        ////自定义字段
        //String batchId = StrUtil.isNotEmpty(newModel.getBatchId()) ? newModel.getBatchId() : IdUtil.simpleUUID();
        //Long dataId = ObjectUtil.isNotNull(newModel.getShortfallId()) ? newModel.getShortfallId() : BaseUtil.getNextId();
        //fieldDataService.saveData(crmModel.getField(), batchId, crmModel.getSaveType(), getLabel(), dataId);
        if (ObjectUtil.isEmpty(newModel.getId())){
            save(newModel);
            crmActivityService.addActivity(2, CrmActivityEnum.CUSTOMER, crmCustomer.getCustomerId());
            CrmActionRecord crmActionRecord = actionRecordUtil.addRecord(crmCustomer.getCustomerId(), CrmEnum.CUSTOMER, crmCustomer.getCustomerName());

            operationLog.setOperationObject(newModel.getCustomerId(), newModel.getCustomerName());
            operationLog.setOperationInfo(crmActionRecord.getDetail());
        }else {
            ${entity} old = getById(newModel.getId());
            newModel.setUpdateTime(LocalDateTime.now());

            Map<String, Object> oldMap = BeanUtil.beanToMap(old);
            Map<String, Object> newMap = BeanUtil.beanToMap(newModel);
            updateById(newModel);

            actionRecordUtil.updateRecord(BeanUtil.beanToMap(old), BeanUtil.beanToMap(newModel), CrmEnum.CUSTOMER, newModel.getName(), newModel.getId());
            List<String> updateRecord = actionRecordUtil.updateRecord(oldMap, newMap, CrmEnum.CUSTOMER, newModel.getName(), newModel.getId(), crmModel.getSaveType());

            operationLog.setOperationObject(newModel.getCustomerId(), newModel.getCustomerName());
            operationLog.setOperationInfo(JSONUtil.toJsonStr(updateRecord));
            operationLogList.add(operationLog);
        }

        ${entity} retModel = getById(newModel.getId());
        crmModel.setEntity(BeanUtil.beanToMap(retModel));
        savePage(crmModel, retModel.getId(), isExcel);

        Map<String, Object> map = new HashMap<>();
        map.put("customerId", retModel.getCustomerId());
        map.put("customerName", retModel.getCustomerName());
        map.put("operation", operationLogList);
        return map;
    }


    /**
    * 查询字段配置
    *
    * @param id 主键ID
    * @return data
    */

    @Override
    public CrmModel queryById(Long id) {
        return queryById(id, true);
    }
    @Override
    public CrmModel queryById(Long id , boolean isAuth) {
        CrmModel crmModel;
        if (id != null) {
            crmModel = getBaseMapper().queryById(id);
            Long ownerUserId = crmModel.getOwnerUserId();

            crmModel.setLabel(getLabel().getType());
            crmModel.setOwnerUserName(UserCacheUtil.getUserName(ownerUserId));
            fieldDataService.setDataByBatchId(crmModel, getLabel());
            if (isAuth) {
                ApplicationContextHolder.getBean(ICrmRoleFieldService.class).handleNoAuthField(getLabel(), crmModel);
            }
        } else {
            crmModel = new CrmModel(CrmEnum.CUSTOMER.getType());
        }
        return crmModel;
    }
    /**
    * 查询详情
    *
    * @param id     主键ID
    */
    @Override
    public List<CrmModelFieldVO> information(Long id) {
        List<CrmModelFieldVO> collect = queryField(id);
        //collect.removeIf(r -> Arrays.asList("ownerUserId", "owner_user_name").contains(r.getFieldName()));
        return collect;
    }
    /**
    * 删除客户数据
    *
    * @param ids ids
    */
    @Override
    public List<OperationLog> deleteByIds(List<Long> ids) {
        removeByIds(ids);
        //删除文件
        //adminFileService.delete(batchList);
        //删除跟进记录
        //crmActivityService.deleteActivityRecord(ids);
        //删除字段操作记录
        //crmActionRecordService.deleteActionRecord(CrmEnum.CUSTOMER, ids);
        //删除自定义字段
        //fieldDataService.deleteByDataId(ids, getLabel());
        //crmBackLogDealService.deleteByTypeIds(ids, CrmBackLogEnum.TO_ENTER_CUSTOMER_POOL);
        deletePage(ids);
        List<OperationLog> operationLogList = new ArrayList<>();
        return operationLogList;
    }
    /**
    * 修改客户负责人
    *
    * @param changOwnerUserBO data
    */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<OperationLog> changeOwnerUser(CrmChangeOwnerUserBO changOwnerUserBO) {

        Long ownerUserId = changOwnerUserBO.getOwnerUserId();
        String ownerUserName = UserCacheUtil.getUserName(ownerUserId);

        List<OperationLog> operationLogList = new ArrayList<>();
        changOwnerUserBO.getIds().forEach(id -> {
            ${entity} contract = getById(id);
            //int eight = 8;
            //if (contract.getCheckStatus() == eight) {
            //    throw new CrmException(CrmCodeEnum.CRM_CONTRACT_TRANSFER_ERROR, getLabel().getRemarks());
            //}
            OperationLog operationLog = new OperationLog();
            operationLog.setOperationObject(id, contract.getName());

            int two = 2;
            if (two == changOwnerUserBO.getTransferType() && !ownerUserId.equals(contract.getOwnerUserId())) {
                ApplicationContextHolder.getBean(ICrmTeamMembersService.class).addSingleMember(getLabel(), contract.getContractId(), contract.getOwnerUserId(), changOwnerUserBO.getPower(), changOwnerUserBO.getExpiresTime(), contract.getName());
            }
            ApplicationContextHolder.getBean(ICrmTeamMembersService.class).deleteMember(getLabel(), new CrmMemberSaveBO(id, ownerUserId));
            CrmActionRecord crmActionRecord = actionRecordUtil.addConversionRecord(id, CrmEnum.CONTRACT, ownerUserId, contract.getName(), contract.getOwnerUserId());

            operationLog.setOperationInfo(crmActionRecord.getDetail());
            operationLog.setApplyObject(OperateObjectEnum.CONTRACT);
            operationLogList.add(operationLog);
        });
        LambdaUpdateWrapper<${entity}> wrapper = new LambdaUpdateWrapper<>();
        wrapper.in(${entity}::getContractId, changOwnerUserBO.getIds());
        wrapper.set(${entity}::getOwnerUserId, ownerUserId);
        update(wrapper);
        //修改es
        Map<String, Object> map = new HashMap<>();
        map.put("ownerUserId", ownerUserId);
        map.put("ownerUserName", ownerUserName);
        updateField(map, changOwnerUserBO.getIds());
        return operationLogList;
    }
    @Override
    public CrmModel queryDigestById(Long id) {
        return getBaseMapper().queryDigestById(id);
    }

    @Override
    public void exportExcel(HttpServletResponse response, CrmSearchBO search, List<Long> sortIds,CrmExportBO exportBO) {
        Integer isXls = exportBO.getIsXls();
        List<CrmFieldSortVO> headList = crmFieldService.queryListHead(getLabel().getType(), sortIds);

        BasePage<Map<String, Object>> basePage = queryList(search);
        //根据合同ID查询产品
        List<Map<String, Object>> dataList = new LinkedList<>();

        List<ExcelParseUtil.ExcelMergeEntity> mergeEntityList = new ArrayList<>();

        List<Long> longs = basePage.getList().stream().map(data -> TypeUtils.castToLong(data.get("shortfallId"))).collect(Collectors.toList());
        CrmRelationPageBO crmRelationPageBO = new CrmRelationPageBO();
        crmRelationPageBO.setContractIds(longs);
        crmRelationPageBO.setPageType(0);
        //产品
        List<JSONObject> productList = getbaseMapper().queryProductList(longs);
        Map<Long, List<JSONObject>> productMap = productList.stream().collect(Collectors.groupingBy(CrmShortfallProduct::getShortfallId));

        basePage.getList().forEach(map -> {
            if (productMap.containsKey(TypeUtils.castToLong(map.get("contractId")))) {
                List<JSONObject> objectList = productMap.get(TypeUtils.castToLong(map.get("contractId")));
                if (isXls == 1) {
                    if (dataList.size() <= 10000) {
                        int firstRow = dataList.size() + 1;
                        int lastRow = dataList.size() + Math.max(objectList.size(), 1);
                        if (firstRow != lastRow) {
                            for (String fieldName : map.keySet()) {
                                ExcelParseUtil.ExcelMergeEntity mergeEntity = new ExcelParseUtil.ExcelMergeEntity();
                                mergeEntity.setFieldName(fieldName);
                                mergeEntity.setContent(map.get(fieldName));
                                mergeEntity.setFirstRow(firstRow);
                                mergeEntity.setLastRow(lastRow);
                                mergeEntityList.add(mergeEntity);
                            }
                        }
                    }
                }
                for (int i = 0; i < objectList.size(); i++) {
                    Map<String, Object> product = BeanUtil.beanToMap(objectList.get(i));
                    if (isXls == 2) {
                        if (i != 0) {
                            map = map.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> ""));
                        }
                    }
                    product.putAll(map);
                    dataList.add(product);
                }
            } else {
                dataList.add(map);
            }
        });
        ExcelParseUtil.exportExcel(dataList, new ExcelParseUtil.ExcelParseService() {

            @Override
            public String getExcelName() {
                return getLabel().getRemarks();
            }

            @Override
            public List<ExcelParseUtil.ExcelMergeEntity> getMergeData() {
                return dataList.size() > 10000 ? null : mergeEntityList;
            }

            /**
            * 设置自定义数据处理方法
            *
            * @return func
            */
            @Override
            public ExcelParseUtil.DataFunc getFunc() {
                return (record, headMap) -> {
                    for (String fieldName : headMap.keySet()) {
                        record.put(fieldName, RecordValueParseUtil.parseExportValue(record.get(fieldName), headMap.get(fieldName), false));
                    }
                    Object status = record.get("checkStatus");
                    if (status instanceof Integer) {
                        Object name;
                        switch ((Integer) status) {
                            case 1:
                                name = "审核通过";
                            break;
                            case 2:
                                name = "拒绝";
                            break;
                            case 3:
                                name = "审核中";
                            break;
                            case 4:
                                name = "撤回";
                            break;
                            case 8:
                                name = "作废";
                            break;
                            default:
                                name = "正常";
                            break;
                        }
                        record.put("checkStatus", name);
                    }
                };
            }

            /**
            * 是否是xlsx格式，xlsx导出会比xlx3倍左右，谨慎使用
            *
            * @return isXlsx
            */
            @Override
            public boolean isXlsx() {
                return true;
            }

            /**
            * 如果需要执行全部数据导出，分批次的获取数据
            *
            * @return data
            */
            @Override
            public List<? extends Map<String, Object>> getNextData() {
                BasePage<Map<String, Object>> mapBasePage = queryList(search);
                return mapBasePage.getList();
            }
        }, headList, response, isXls, basePage.getTotal() == basePage.getList().size());

    }

    /**
    * 判断类型
    */
    private void setData(Map<String, Object> entity) {
        List<CrmField> fieldList = crmFieldService.lambdaQuery().eq(CrmField::getLabel, getLabel().getType()).eq(CrmField::getFieldType, 1).list();
        for (CrmField crmField : fieldList) {
            if (entity.get(crmField.getFieldName()) != null) {
            String value = FieldUtil.format2DbString(entity.get(crmField.getFieldName()), crmField.getType(), entity.get(crmField.getFieldName()).toString());
            entity.put(crmField.getFieldName(), value);
            }
        }
    }

}
</#if>
