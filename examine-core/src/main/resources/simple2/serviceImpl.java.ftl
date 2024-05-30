package ${package.ServiceImpl};

import ${package.Entity}.${entity};
import ${package.Mapper}.${table.mapperName};
import ${package.Service}.${table.serviceName};
import ${superServiceImplClassPackage};
import org.springframework.stereotype.Service;

import com.unique.core.utils.FieldUtil;
import com.unique.core.context.ConstModule;
import com.unique.core.entity.base.bo.SearchBO;
import com.unique.core.common.BasePage;

import com.unique.examine.entity.po.ModuleField;
import com.unique.examine.entity.po.ModuleRecordData;

import com.unique.examine.service.IModuleFieldService;
import com.unique.examine.service.IModuleRecordDataService;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;

import cn.hutool.core.util.ObjectUtil;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

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

    @Autowired
    private IModuleFieldService moduleFieldService;
    @Autowired
    private IModuleRecordDataService moduleRecordDataService;

    /**
    * 导出时查询所有数据
    *
    * @param search 业务查询对象
    * @return data
    */
    @Override
    public BasePage<Map<String, Object>> queryPageList(SearchBO search) {
        BasePage<Map<String, Object>> basePage = getBaseMapper().queryPageList(search.parse(),search);
        return basePage;
    }
    /**
    * 查询字段配置
    *
    * @param id 主键ID
    * @return data
    */
    @Override
    public List<ModuleField> queryField(Long id) {
        Map<String, Object> record = queryById(id);
        List<ModuleField> vos = new ArrayList<>();
        if (ObjectUtil.isNotEmpty(record.get(ConstModule.MODULE_ID))) {
            vos = moduleFieldService.queryField(Long.valueOf(record.get(ConstModule.MODULE_ID).toString()));
            if (CollectionUtil.isNotEmpty(vos)) {
                vos.forEach(r->{
                    if (ObjectUtil.isNotEmpty(record.get(r.getFieldName()))) {
                        r.setDefaultValue(record.get(r.getFieldName()).toString());
                    }
                });
            }
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
    public List<List<ModuleField>> queryFormField(Long id) {
        List<ModuleField> fieldList = queryField(id);
        List<List<ModuleField>> vos = FieldUtil.getFieldFormList(fieldList,ModuleField::getAxisy,ModuleField::getAxisx);

        for (List<ModuleField> filedVOList : vos) {
            filedVOList.forEach(field -> {
<#--                if ("ownerUserId".equals(field.getFieldName())) {-->
<#--                    SimpleUser user = new SimpleUser();-->
<#--                    user.setUserId(UserUtil.getUserId());-->
<#--                    user.setRealname(UserUtil.getUser().getRealname());-->
<#--                    field.setDefaultValue(Collections.singleton(user));-->
<#--                }-->
            });
        }

        return vos;
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
            newModel.setCreateTime(LocalDateTime.now());
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
    public Map<String, Object> queryById(Long id) {
        LambdaQueryWrapper<${entity}> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(${entity}::getId, id);
        Map<String, Object> recordMap = getMap(queryWrapper);
        if (ObjectUtil.isNotEmpty(recordMap)) {
            List<ModuleRecordData> dataList = moduleRecordDataService.lambdaQuery().eq(ModuleRecordData::getRecordId, id).list();
            if (CollectionUtil.isNotEmpty(dataList)) {
                dataList.forEach(f->{
                    recordMap.put(f.getName(),f.getValue());
                });
            }
        }
        return recordMap;
    }

    /**
    * 查询详情
    *
    * @param id     主键ID
    */
    @Override
    public List<ModuleField> information(Long id) {
        List<ModuleField> collect = queryField(id);
        return collect;
    }

    /**
    * 删除客户数据
    *
    * @param ids ids
    */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(List<Long> ids) {
        removeByIds(ids);
        LambdaQueryWrapper<ModuleRecordData> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(ModuleRecordData::getRecordId,ids);
        moduleRecordDataService.remove(queryWrapper);
        //删除字段操作记录
        //crmActionRecordService.deleteActionRecord(CrmEnum.CUSTOMER, ids);
    }

}
</#if>
