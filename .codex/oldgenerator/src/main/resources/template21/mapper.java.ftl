package ${package.Mapper};

import ${package.Entity}.${entity};
import ${superMapperClassPackage};
<#if mapperAnnotation>
import org.apache.ibatis.annotations.Mapper;
</#if>

/**
 * <p>
 * ${table.comment!} Mapper 接口
 * </p>
 *
 * @author ${author}
 * @since ${date}
 */
<#if mapperAnnotation>
@Mapper
</#if>
<#if kotlin>
interface ${table.mapperName} : ${superMapperClass}<${entity}>
<#else>
public interface ${table.mapperName} extends ${superMapperClass}<${entity}> {
 /**
 * 通过id查询商机数据
 *
 * @param id     id
 * @param userId 用户ID
 * @return data
 */
 CrmModel queryById(@Param("id") Long id, @Param("userId") Long userId);

 /**
 * 查询产品分页
 *
 */
 List<JSONObject> queryProductList(@Param("ids") List<Long> ids);
}
</#if>
