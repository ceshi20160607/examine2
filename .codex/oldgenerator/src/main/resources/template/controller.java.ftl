package ${package.Controller};

<#if superControllerClassPackage??>
import ${superControllerClassPackage};
</#if>
import com.kakarote.core.common.Result;
import ${package.Entity}.${table.entityName};
import ${package.Service}.${table.serviceName};
import com.kakarote.core.entity.BasePage;
import com.kakarote.core.entity.PageEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.Serializable;
import java.util.List;

/**
 * <p>
 * ${table.comment!} 前端控制器
 * </p>
 *
 * @author ${author}
 * @since ${date}
 */
<#if restControllerStyle>
@RestController
<#else>
@Controller
</#if>
@RequestMapping("<#if package.ModuleName?? && package.ModuleName != "">/${package.ModuleName}</#if>/<#if controllerMappingHyphenStyle??>${controllerMappingHyphen}<#else>${table.entityPath}</#if>")
<#if kotlin>
class ${table.controllerName}<#if superControllerClass??> : ${superControllerClass}()</#if>
<#else>
<#if superControllerClass??>
public class ${table.controllerName} extends ${superControllerClass} {
<#else>
public class ${table.controllerName} {
</#if>

    @Autowired
    private ${table.serviceName} ${serviceName};

    @PostMapping("/queryById/{id}")
    @Operation(summary = "根据ID查询")
    public Result<${table.entityName}> queryById(@PathVariable("id") @Parameter(name = "id", description = "id") Serializable id) {
        ${table.entityName} entity = ${serviceName}.queryById(id);
        return Result.ok(entity);
    }

    @PostMapping("/add")
    @Operation(summary = "保存数据")
    public Result<String> add(@RequestBody ${table.entityName} entity) {
        ${serviceName}.addOrUpdate(entity);
        return Result.ok();
    }

    @PostMapping("/update")
    @Operation(summary = "修改数据")
    public Result<String> update(@RequestBody ${table.entityName} entity) {
        ${serviceName}.addOrUpdate(entity);
        return Result.ok();
    }

    @PostMapping("/queryPageList")
    @Operation(summary = "查询列表页数据")
    public Result<BasePage<${table.entityName}>> queryPageList(@RequestBody PageEntity search) {
        search.setPageType(1);
        return Result.ok(${serviceName}.queryPageList(search));
    }

    @PostMapping("/deleteByIds")
    @Operation(summary = "根据ID删除数据")
    public Result<String> deleteByIds(@Parameter(name = "id", description = "id") @RequestBody List<Serializable> ids) {
        ${serviceName}.deleteByIds(ids);
        return Result.ok();
    }
}
</#if>
