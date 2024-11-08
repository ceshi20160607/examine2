package ${package.Service};

import ${package.Entity}.${entity};
import ${superServiceClassPackage};

import com.unique.core.entity.base.bo.SearchBO;
import com.unique.core.common.BasePage;

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
    BasePage<${entity}> queryPageList(SearchBO search);

    /**
    * 保存或新增信息
    *
    * @param baseModel
    */
    void addOrUpdate(${entity} baseModel, boolean isExcel);

    /**
    * 查询字段配置
    *
    * @param id     主键ID
    * @return data
    */
    ${entity} queryById(Long id);

    /**
    * 删除客户数据
    *
    * @param ids ids
    */
    void deleteByIds(List<Long> ids);

}
</#if>
