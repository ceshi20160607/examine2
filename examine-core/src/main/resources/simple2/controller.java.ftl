package ${package.Controller};


import cn.hutool.core.util.ObjectUtil;
import com.unique.core.entity.base.bo.SearchBO;
import com.unique.core.common.BasePage;
import com.unique.core.common.Result;
import com.unique.examine.entity.po.ModuleField;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


import ${package.Service}.${table.serviceName};
import ${package.Entity}.${entity};

<#if restControllerStyle>
import org.springframework.web.bind.annotation.RestController;
<#else>
import org.springframework.stereotype.Controller;
</#if>
<#if superControllerClassPackage??>
import ${superControllerClassPackage};
</#if>

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
@Api(tags = "${table.comment!}")
public class ${table.controllerName} {
</#if>

    @Autowired
    private ${table.serviceName} ${table.serviceName?substring(1)?uncap_first};


    /**
    * 查询所有数据
    *
    * @param search 业务查询对象
    * @return data
    */
    @PostMapping("/queryPageList")
    @ApiOperation("查询列表页数据")
    public Result<BasePage<Map<String, Object>>> queryPageList(@RequestBody SearchBO search) {
        search.setPageType(1);
        BasePage<Map<String, Object>> mapBasePage = ${table.serviceName?substring(1)?uncap_first}.queryPageList(search);
        return Result.ok(mapBasePage);
    }
    /**
    * 新建页面字段
    *
    */
    @PostMapping("/queryFieldAdd")
    @ApiOperation("查询新增所需字段")
    public Result<List> queryFieldAdd(@RequestParam(value = "type", required = false) String type) {
        if (ObjectUtil.isNotEmpty(type)) {
            return Result.ok(${table.serviceName?substring(1)?uncap_first}.queryField(null));
        }
        return Result.ok(${table.serviceName?substring(1)?uncap_first}.queryFormField(null));
    }

    /**
    * 编辑页面字段
    *
    * @param id
    */
    @PostMapping("/queryFieldEdit/{id}")
    @ApiOperation("查询修改数据所需信息")
    public Result<List> queryFieldEdit(@PathVariable("id") @ApiParam(name = "id", value = "id") Long id,
        @RequestParam(value = "type", required = false) String type) {
        if (ObjectUtil.isNotEmpty(type)) {
            List<ModuleField> collect = ${table.serviceName?substring(1)?uncap_first}.queryField(id).stream().filter(field -> !field.getFieldName().equals("ownerUserId")).collect(Collectors.toList());
            return Result.ok(collect);
        }
        return Result.ok(${table.serviceName?substring(1)?uncap_first}.queryFormField(id));
    }
    /**
    * 保存数据
    *
    * @param crmModel 业务对象
    * @return data
    */
    @PostMapping("/add")
    @ApiOperation("保存数据")
    public Result<Map<String, Object>> add(@RequestBody ${entity} crmModel) {
        Map<String, Object> map = ${table.serviceName?substring(1)?uncap_first}.addOrUpdate(crmModel, false);
        return Result.ok(map);
    }
    /**
    * 更新数据
    *
    * @param crmModel 业务对象
    * @return data
    */
    @PostMapping("/update")
    @ApiOperation("修改数据")
    public Result<Map<String, Object>> update(@RequestBody ${entity} crmModel) {
        Map<String, Object> map = ${table.serviceName?substring(1)?uncap_first}.addOrUpdate(crmModel, false);
        return Result.ok(map);
    }
    /**
    * 查询数据
    * @param id 业务对象id
    * @return data
    */
    @PostMapping("/queryById/{id}")
    @ApiOperation("根据ID查询")
    public Result<Map<String, Object> > queryById(@PathVariable("id") @ApiParam(name = "id", value = "id") Long id) {
        Map<String, Object>  model = ${table.serviceName?substring(1)?uncap_first}.queryById(id);
        return Result.ok(model);
    }
    /**
    * 查询详情页基本信息
    *
    * @param  id
    * @return data
    */
    @PostMapping("/information/{id}")
    @ApiOperation("查询详情页信息")
    public Result<List<ModuleField>> information(@PathVariable("id") @ApiParam(name = "id", value = "id") Long id) {

        List<ModuleField> information = ${table.serviceName?substring(1)?uncap_first}.information(id);

        return Result.ok(information);
    }

    /**
    * 删除数据
    * @param ids 业务对象ids
    * @return data
    */
    @PostMapping("/deleteByIds")
    @ApiOperation("根据ID删除数据")
    public Result deleteByIds(@ApiParam(name = "ids", value = "id列表") @RequestBody List<Long> ids) {
        ${table.serviceName?substring(1)?uncap_first}.deleteByIds(ids);
        return Result.ok();
    }


}
</#if>

