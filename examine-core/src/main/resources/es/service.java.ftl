package ${package.Service};

import ${package.Entity}.${entity};
import ${superServiceClassPackage};

import com.unique.common.log.entity.OperationLog;
import com.unique.crm.common.CrmModel;
import com.unique.crm.entity.BO.*;
import com.unique.core.entity.BasePage;
import com.unique.crm.entity.VO.CrmModelFieldVO;

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
    BasePage<Map<String, Object>> queryPageList(CrmSearchBO search);
    /**
    * 查询字段配置
    *
    * @param id 主键ID
    */
    List<CrmModelFieldVO> queryField(Long id);
    /**
    * 查询字段配置
    *
    * @param id 主键ID
    */
    List<List<CrmModelFieldVO>> queryFormPositionField(Long id);

    /**
    * 保存或新增信息
    *
    * @param baseModel model
    */
    Map<String, Object> addOrUpdate(CrmBusinessSaveBO baseModel, boolean isExcel);

    /**
    * 查询字段配置
    *
    * @param id     主键ID
    * @return data
    */
    CrmModel queryById(Long id);

    /**
    * 查询详情
    *
    * @param id     主键ID
    */
    public List<CrmModelFieldVO> information(Long id);

    /**
    * 删除客户数据
    *
    * @param ids ids
    */
    List<OperationLog> deleteByIds(List<Long> ids);

    /**
    * 根据客户id，获取客户摘要
    *
    * @param id 主键ID
    * @return data
    */
    CrmModel queryDigestById(Long id);

    void exportExcel(HttpServletResponse response, CrmSearchBO search, List<Long> sortIds, CrmExportBO exportBO);
}
</#if>
