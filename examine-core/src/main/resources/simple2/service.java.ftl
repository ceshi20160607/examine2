package ${package.Service};

import ${package.Entity}.${entity};
import ${superServiceClassPackage};

import com.unique.core.entity.base.bo.SearchBO;
import com.unique.core.common.BasePage;

import com.unique.examine.entity.po.ModuleField;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * ${table.comment!} 服务类
 * </p>
 *
 * @author ${author}
 * @since ${date}
 */
<#if kotlin>
interface ${table.serviceName} : ${superServiceClass}<${entity}>
<#else>
public interface ${table.serviceName} extends ${superServiceClass}<${entity}> {


    /**
    * 查询所有数据
    *
    * @param search 搜索数据
    * @return data
    */
    BasePage<Map<String, Object>> queryPageList(SearchBO search);
    /**
    * 查询字段配置
    *
    * @param id 主键ID
    */
    List<ModuleField> queryField(Long id);
    /**
    * 查询字段配置
    *
    * @param id 主键ID
    */
    List<List<ModuleField>> queryFormField(Long id);

    /**
    * 保存或新增信息
    *
    * @param crmModel
    */
    Map<String, Object> addOrUpdate(${entity} crmModel, boolean isExcel);

    /**
    * 查询字段配置
    *
    * @param id     主键ID
    * @return data
    */
    Map<String, Object>  queryById(Long id);

    /**
    * 查询详情
    *
    * @param id     主键ID
    */
    public List<ModuleField> information(Long id);


    /**
    * 删除客户数据
    *
    * @param ids ids
    */
    void deleteByIds(List<Long> ids);

}
</#if>
