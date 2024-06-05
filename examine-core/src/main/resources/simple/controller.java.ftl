package ${package.Controller};


import com.unique.common.log.annotation.OperateLog;
import com.unique.common.log.entity.OperationResult;
import com.unique.common.log.enums.ApplyEnum;
import com.unique.common.log.enums.BehaviorEnum;
import com.unique.common.log.enums.OperateObjectEnum;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import com.unique.crm.entity.VO.CrmModelFieldVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import com.unique.core.common.Result;
import com.unique.core.entity.BasePage;
import com.unique.crm.entity.BO.*;
import com.unique.common.log.entity.OperationLog;

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
    private ${table.serviceName} ${table.serviceName?uncap_first};


    /**
    * 查询所有数据
    *
    * @param search 业务查询对象
    * @return data
    */
    @PostMapping("/queryPageList")
    @ApiOperation("查询列表页数据")
    public Result<BasePage<Map<String, Object>>> queryPageList(@RequestBody CrmSearchBO search) {
        search.setPageType(1);
        BasePage<Map<String, Object>> mapBasePage = ${table.serviceName?uncap_first}.queryPageList(search);
        return Result.ok(mapBasePage);
    }
    /**
    * 保存数据
    *
    * @param baseModel 业务对象
    * @return data
    */
    @PostMapping("/add")
    @ApiOperation("保存数据")
    @OperateLog(behavior = BehaviorEnum.SAVE, apply = ApplyEnum.CRM, object = OperateObjectEnum.CUSTOMER)
    public Result<Map<String, Object>> add(@RequestBody ${entity} baseModel) {
        Map<String, Object> map = ${table.serviceName?uncap_first}.addOrUpdate(baseModel, false);
        Object operation = map.get("operation");
        map.remove("operation");
        return OperationResult.ok(map, (List<OperationLog>) operation);
    }
    /**
    * 更新数据
    *
    * @param baseModel 业务对象
    * @return data
    */
    @PostMapping("/update")
    @ApiOperation("修改数据")
    @OperateLog(behavior = BehaviorEnum.UPDATE, apply = ApplyEnum.CRM, object = OperateObjectEnum.CUSTOMER)
    public Result<Map<String, Object>> update(@RequestBody ${entity} baseModel) {
        Map<String, Object> map = ${table.serviceName?uncap_first}.addOrUpdate(baseModel, false);
        Object operation = map.get("operation");
        map.remove("operation");
        return OperationResult.ok(map, (List<OperationLog>) operation);
    }
    /**
    * 查询数据
    * @param id 业务对象id
    * @return data
    */
    @PostMapping("/queryById/{id}")
    @ApiOperation("根据ID查询")
    public Result<${entity}> queryById(@PathVariable("id") @ApiParam(name = "id", value = "id") Long id) {
        ${entity} model = ${table.serviceName?uncap_first}.queryById(id);
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
    public Result<List<CrmModelFieldVO>> information(@PathVariable("id") @ApiParam(name = "id", value = "id") Long id) {

        List<CrmModelFieldVO> information = ${table.serviceName?uncap_first}.information(id);

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
        List<OperationLog> operationLogList = ${table.serviceName?uncap_first}.deleteByIds(ids);
        return OperationResult.ok(operationLogList);
    }


}
</#if>

