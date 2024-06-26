package ${package.ServiceImpl};

import ${package.Entity}.${entity};
import ${package.Mapper}.${table.mapperName};
import ${package.Service}.${table.serviceName};
import ${superServiceImplClassPackage};
import org.springframework.stereotype.Service;


import com.unique.core.exception.CrmException;
import com.unique.crm.constant.CrmCodeEnum;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.common.field.entity.ModelField;
import com.unique.crm.common.ActionRecordUtil;
import com.unique.crm.constant.CrmEnum;
import com.unique.crm.entity.PO.CrmField;
import com.unique.crm.entity.VO.CrmModelFieldVO;
import com.unique.crm.service.ICrmActionRecordService;
import com.unique.crm.service.ICrmFieldService;
import org.springframework.beans.factory.annotation.Autowired;

import cn.hutool.core.util.ObjectUtil;
import com.unique.common.log.entity.OperationLog;
import com.unique.core.entity.BasePage;
import com.unique.crm.entity.BO.*;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
public class ${table.serviceImplName} extends ${superServiceImplClass}<${table.mapperName}, ${entity}> implements ${table.serviceName} {

//    @Autowired
//    private ICrmActionRecordService crmActionRecordService;
//    @Autowired
//    private ActionRecordUtil actionRecordUtil;
    @Autowired
    private ICrmFieldService crmFieldService;

    /**
    * 导出时查询所有数据
    *
    * @param search 业务查询对象
    * @return data
    */
    @Override
    public BasePage<Map<String, Object>> queryPageList(CrmSearchBO search) {
        BasePage<Map<String, Object>> basePage = getBaseMapper().queryPageList(search.parse(),search);
        return basePage;
    }
    /**
    * 保存或新增信息
    *
    * @param newModel
    */
    @Override
    public Map<String, Object> addOrUpdate(${entity} newModel, boolean isExcel) {
        Map<String, Object> map = new HashMap<>();
        if (ObjectUtil.isEmpty(newModel.getId())){
            save(newModel);
            //actionRecordUtil.addRecord(newModel.getId(), CrmEnum.CUSTOMER, newModel.getName());
        }else {
            ${entity}  old = getById(newModel.getId());
            newModel.setUpdateTime(LocalDateTime.now());
            updateById(newModel);
            //actionRecordUtil.updateRecord(BeanUtil.beanToMap(old), BeanUtil.beanToMap(newModel), CrmEnum.CUSTOMER, newModel.getName(), newModel.getId());
        }
        map.put("id", newModel.getId());
        return map;
    }


    /**
    * 查询字段配置
    *
    * @param id 主键ID
    * @return data
    */
    @Override
    public ${entity} queryById(Long id) {
        ${entity} byId = getBaseMapper().queryById(id);
        if (ObjectUtil.isNotEmpty(byId)) {
            return byId;
        }else{
            throw new CrmException(CrmCodeEnum.CRM_REQUEST_PARAMS_ERROR);
        }
    }

    /**
    * 查询详情
    *
    * @param id     主键ID
    */
    @Override
    public List<CrmModelFieldVO> information(Long id) {
        List<CrmModelFieldVO> collect = queryField(id);
        return collect;
    }

    /**
    * 删除客户数据
    *
    * @param ids ids
    */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<OperationLog> deleteByIds(List<Long> ids) {
        removeByIds(ids);
        //删除字段操作记录
        //crmActionRecordService.deleteActionRecord(CrmEnum.CUSTOMER, ids);
        List<OperationLog> operationLogList = new ArrayList<>();
        return operationLogList;
    }

    /** 查询字段配置
    * @param id
    * @return {@link List}<{@link CrmModelFieldVO}>
    */
    public List<CrmModelFieldVO> queryField(Long id) {
        List<CrmModelFieldVO> ret = new ArrayList<>();
        LambdaQueryWrapper<${entity}> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(${entity}::getId, id);
        List<Map<String, Object>> maps = listMaps(queryWrapper);
        List<CrmField> fieldList = crmFieldService.lambdaQuery().eq(ModelField::getLabel, CrmEnum.CUSTOMER.getType()).list();
            fieldList.forEach(r->{
            CrmModelFieldVO crmModelFiled = BeanUtil.copyProperties(r, CrmModelFieldVO.class);
            String fieldName = StrUtil.toCamelCase(r.getFieldName());
            crmModelFiled.setValue(maps.get(0).get(fieldName));
            ret.add(crmModelFiled);
        });
        return ret;
    }
}
</#if>
