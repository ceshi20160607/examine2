package ${package.Mapper};

import ${package.Entity}.${entity};
import ${superMapperClassPackage};
import org.apache.ibatis.annotations.Mapper;

/**
 * ${table.comment!} 基础 Mapper。
 *
 * @author ${author}
 * @since generated
 */
@Mapper
public interface ${table.mapperName} extends ${superMapperClass}<${entity}> {
}
