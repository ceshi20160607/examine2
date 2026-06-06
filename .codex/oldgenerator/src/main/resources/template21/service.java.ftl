package ${package.Service};

import ${package.Entity}.${entity};
import ${superServiceClassPackage};
import com.kakarote.common.log.entity.OperationLog;
import com.kakarote.crm.common.CrmModel;
import com.kakarote.crm.entity.BO.CrmBusinessSaveBO;
import com.kakarote.crm.entity.BO.CrmChangeOwnerUserBO;
import com.kakarote.crm.entity.BO.CrmExportBO;
import com.kakarote.crm.entity.BO.CrmSearchBO;
import com.kakarote.crm.entity.VO.CrmModelFieldVO;
import com.kakarote.core.entity.BasePage;
import jakarta.servlet.http.HttpServletResponse;

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
    * @author ${author}
    * @since ${date}
    */
    BasePage<Map<String, Object>> queryPageList(CrmSearchBO search);
    /**
    * 查询字段配置
    * @author ${author}
    * @since ${date}
    */
    List<CrmModelFieldVO> queryField(Long id);
    /**
    * 查询字段配置
    * @author ${author}
    * @since ${date}
    */
    List<List<CrmModelFieldVO>> queryFormPositionField(Long id);

    /**
    * 保存或新增信息
    * @author ${author}
    * @since ${date}
    */
    Map<String, Object> addOrUpdate(CrmBusinessSaveBO baseModel, boolean isExcel);

    /**
    * 查询字段配置
    * @author ${author}
    * @since ${date}
    */
    CrmModel queryById(Long id);
    CrmModel queryById(Long id, boolean isAuth);

    /**
    * 查询详情
    * @author ${author}
    * @since ${date}
    */
    public List<CrmModelFieldVO> information(Long id);

    /**
    * 删除客户数据
    * @author ${author}
    * @since ${date}
    */
    List<OperationLog> deleteByIds(List<Long> ids);

    /**
    * 修改客户负责人
    * @author ${author}
    * @since ${date}
    */
    List<OperationLog> changeOwnerUser(CrmChangeOwnerUserBO changOwnerUserBO);

    /**
    * 根据客户id，获取客户摘要
    * @author ${author}
    * @since ${date}
    */
    CrmModel queryDigestById(Long id);

    /**
    * 导出
    * @author ${author}
    * @since ${date}
    */
    void exportExcel(HttpServletResponse response, CrmSearchBO search, List<Long> sortIds, CrmExportBO exportBO);

}
</#if>
