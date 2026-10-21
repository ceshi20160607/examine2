package ${package.Service};

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import ${package.Entity}.${entity};
import java.io.Serializable;
import java.util.Collection;

/** ${table.comment!entity}的基础 CRUD 服务；业务权限和跨表规则由同模块 manage 层处理。 */
public interface ${table.serviceName} extends IService<${entity}> {
    /** 按调用方限定的查询条件分页。 */
    Page<${entity}> queryPageList(Page<${entity}> page, Wrapper<${entity}> query);

    /** 新增或更新当前表数据；更新目标不存在时明确失败。 */
    ${entity} addOrUpdate(${entity} model);

    /** 按主键读取；不存在时返回 null 供业务服务判断。 */
    ${entity} queryById(Serializable id);

    /** 批量删除当前表数据。 */
    boolean deleteByIds(Collection<? extends Serializable> ids);
}
