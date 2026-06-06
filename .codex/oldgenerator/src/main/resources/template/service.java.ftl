package ${package.Service};

import ${package.Entity}.${entity};
import ${superServiceClassPackage};
import com.kakarote.core.entity.BasePage;
import com.kakarote.core.entity.PageEntity;

import java.io.Serializable;
import java.util.List;

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
     * 查询字段配置
     * @author ${author}
     * @since ${date}
     * @param id 主键ID
     * @return data
     */
    public ${entity} queryById(Serializable id);

    /**
     * 保存或新增信息
     * @author ${author}
     * @since ${date}
     * @param entity entity
     */
    public void addOrUpdate(${entity} entity);


    /**
     * 查询所有数据
     * @author ${author}
     * @since ${date}
     * @param search 搜索条件
     * @return list
     */
    public BasePage<${entity}> queryPageList(PageEntity search);

    /**
     * 根据ID列表删除数据
     * @author ${author}
     * @since ${date}
     * @param ids ids
     */
    public void deleteByIds(List<Serializable> ids);
}
</#if>
