package ${package.Controller};

<#if superControllerClassPackage??>
import ${superControllerClassPackage};
</#if>
import ${package.Entity}.${table.entityName};
import ${package.Service}.${table.serviceName};

import cn.hutool.core.util.StrUtil;
import com.kakarote.common.log.annotation.OperateLog;
import com.kakarote.common.log.entity.OperationLog;
import com.kakarote.common.log.entity.OperationResult;
import com.kakarote.common.log.enums.ApplyEnum;
import com.kakarote.common.log.enums.BehaviorEnum;
import com.kakarote.common.log.enums.OperateObjectEnum;
import com.kakarote.common.log.enums.OperateTypeEnum;
import com.kakarote.core.common.Result;
import com.kakarote.core.common.enums.FieldEnum;
import com.kakarote.core.common.enums.FieldSearchEnum;
import com.kakarote.core.common.enums.SystemCodeEnum;
import com.kakarote.core.entity.Search;
import com.kakarote.core.exception.CrmException;
import com.kakarote.crm.common.AuthUtil;
import com.kakarote.crm.common.CrmModel;
import com.kakarote.crm.constant.CrmAuthEnum;
import com.kakarote.crm.constant.CrmCodeEnum;
import com.kakarote.crm.constant.CrmEnum;
import com.kakarote.crm.entity.BO.*;
import com.kakarote.crm.entity.PO.CrmCustomer;
import com.kakarote.crm.entity.VO.CrmMembersSelectVO;
import com.kakarote.crm.entity.VO.CrmModelFieldVO;
import com.kakarote.core.entity.BasePage;
import com.kakarote.crm.service.ICrmTeamMembersService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    @Autowired
    private ICrmTeamMembersService teamMembersService;

    /**
    * 查询所有数据
    *
    * @param search 业务查询对象
    * @return data
    */
    @PostMapping("/queryPageList")
    @Operation(summary = "查询列表页数据")
    public Result<BasePage<Map<String, Object>>> queryPageList(@RequestBody CrmSearchBO search) {
        search.setPageType(1);
        BasePage<Map<String, Object>> mapBasePage = ${serviceName}.queryPageList(search);
        return Result.ok(mapBasePage);
    }
    /**
    * 新建页面字段
    *
    */
    @PostMapping("/field")
    @Operation(summary = "查询新增所需字段")
    public Result<List> queryField(@RequestParam(value = "type", required = false) String type) {
        if (StrUtil.isNotEmpty(type)) {
            return Result.ok(${serviceName}.queryField(null));
        }
        return Result.ok(${serviceName}.queryFormPositionField(null));
    }

    /**
    * 编辑页面字段
    *
    * @param id
    */
    @PostMapping("/field/{id}")
    @Operation(summary = "查询修改数据所需信息")
    public Result<List> queryFieldPath(@PathVariable("id") @RequestParam(name = "id", value = "id") Long id,
    @RequestParam(value = "type", required = false) String type) {
        if (StrUtil.isNotEmpty(type)) {
        List<CrmModelFieldVO> collect = ${serviceName}.queryField(id).stream().filter(field -> !field.getFieldName().equals("ownerUserId")).collect(Collectors.toList());
            return Result.ok(collect);
        }
        return Result.ok(${serviceName}.queryFormPositionField(id));
    }

    /**
    * 保存数据
    *
    * @param baseModel 业务对象
    * @return data
    */
    @PostMapping("/add")
    @Operation(summary = "保存数据")
    @OperateLog(behavior = BehaviorEnum.SAVE, apply = ApplyEnum.CRM, object = OperateObjectEnum.CUSTOMER)
    public Result<Map<String, Object>> add(@RequestBody CrmBusinessSaveBO baseModel) {
        Map<String, Object> map = ${serviceName}.addOrUpdate(baseModel, false);
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
    @Operation(summary = "修改数据")
    @OperateLog(behavior = BehaviorEnum.UPDATE, apply = ApplyEnum.CRM, object = OperateObjectEnum.CUSTOMER)
    public Result<Map<String, Object>> update(@RequestBody CrmBusinessSaveBO baseModel) {
        Map<String, Object> map = ${serviceName}.addOrUpdate(baseModel, false);
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
    @Operation(summary = "根据ID查询")
    public Result<CrmModel> queryById(@PathVariable("id") @RequestParam(name = "id", value = "id") Long id) {
        boolean exists = ${serviceName}.lambdaQuery().eq( ${entity}::getId, id).ne(${entity}::getStatus, 3).exists();
        if (!exists) {
        throw new CrmException(CrmCodeEnum.CRM_DATA_DELETED, CrmEnum.CUSTOMER.getType());
        }
        CrmModel model = ${serviceName}.queryById(id);
        return Result.ok(model);
    }
    /**
    * 查询详情页基本信息
    *
    * @param  id
    * @return data
    */
    @PostMapping("/information/{id}")
    @Operation(summary = "查询详情页信息")
    public Result<List<CrmModelFieldVO>> information(@PathVariable("id") @RequestParam(name = "id", value = "id") Long id) {
        List<CrmModelFieldVO> information = ${serviceName}.information(id);
        return Result.ok(information);
    }
    /**
    * 删除数据
    * @param ids 业务对象ids
    * @return data
    */
    @PostMapping("/deleteByIds")
    @Operation(summary = "根据ID删除数据")
    public Result deleteByIds(@RequestParam(name = "ids", value = "id列表") @RequestBody List<Long> ids) {
        List<OperationLog> operationLogList = ${serviceName}.deleteByIds(ids);
        return OperationResult.ok(operationLogList);
    }

    @PostMapping("/batchExportExcel")
    @Operation(summary = "选中导出")
    @OperateLog(apply = ApplyEnum.CRM, object = OperateObjectEnum.CUSTOMER, type = OperateTypeEnum.EXPORT, behavior = BehaviorEnum.EXCEL_EXPORT)
    public void batchExportExcel(@RequestBody CrmExportBO exportBO, HttpServletResponse response) {
        CrmSearchBO search = new CrmSearchBO();
        search.setPageType(0);
        search.setLabel(CrmEnum.CUSTOMER.getType());
        Search entity = new Search();
        entity.setFormType(FieldEnum.TEXT.getFormType());
        entity.setSearchEnum(FieldSearchEnum.ID);
        entity.setValues(exportBO.getIds().stream().map(Object::toString).collect(Collectors.toList()));
        search.getSearchList().add(entity);
        search.setPageType(0);
        search.setSortIds(exportBO.getSortIds());
        ${serviceName}.exportExcel(response, exportBO.getSearch(), exportBO.getSortIds(), exportBO);
    }

    @PostMapping("/allExportExcel")
    @Operation(summary = "全部导出")
    @OperateLog(apply = ApplyEnum.CRM, object = OperateObjectEnum.CUSTOMER, type = OperateTypeEnum.EXPORT, behavior = BehaviorEnum.EXCEL_EXPORT)
    public void allExportExcel(@RequestBody CrmExportBO exportBO, HttpServletResponse response) {
        exportBO.getSearch().setPageType(0);
        exportBO.getSearch().setSortIds(exportBO.getSortIds());
        ${serviceName}.exportExcel(response, exportBO.getSearch(), exportBO.getSortIds(), exportBO);
    }


    @PostMapping("/changeOwnerUser")
    @Operation(summary = "修改客户负责人")
    @OperateLog(behavior = BehaviorEnum.CHANGE_OWNER, apply = ApplyEnum.CRM, object = OperateObjectEnum.CUSTOMER)
    public Result changeOwnerUser(@RequestBody CrmChangeOwnerUserBO crmChangeOwnerUserBO) {
        List<OperationLog> operationLogList = ${serviceName}.changeOwnerUser(crmChangeOwnerUserBO);
        return OperationResult.ok(operationLogList);
    }

    @PostMapping("/getMembers/{id}")
    @Operation(summary = "获取团队成员")
    public Result<List<CrmMembersSelectVO>> getMembers(@PathVariable("id") @Parameter(name = "客户ID") Long id) {
        CrmEnum crmEnum = CrmEnum.CUSTOMER;
        ${entity} customer = ${serviceName}.getById(id);
        if (customer == null) {
        throw new CrmException(CrmCodeEnum.CRM_DATA_DELETED, crmEnum.getRemarks());
        }
        List<CrmMembersSelectVO> members = teamMembersService.getMembers(crmEnum, id, customer.getOwnerUserId());
        return Result.ok(members);
    }

    @PostMapping("/addMembers")
    @Operation(summary = "新增团队成员")
    @OperateLog(behavior = BehaviorEnum.ADD_MEMBER, apply = ApplyEnum.CRM, object = OperateObjectEnum.CUSTOMER)
    public Result addMembers(@RequestBody CrmMemberSaveBO crmMemberSaveBO) {
        List<OperationLog> operationLogList = teamMembersService.addMember(CrmEnum.CUSTOMER, crmMemberSaveBO);
        return OperationResult.ok(operationLogList);
    }

    @PostMapping("/updateMembers")
    @Operation(summary = "修改团队成员")
    @OperateLog(behavior = BehaviorEnum.ADD_MEMBER, apply = ApplyEnum.CRM, object = OperateObjectEnum.CUSTOMER)
    public Result updateMembers(@RequestBody CrmMemberSaveBO crmMemberSaveBO) {
        List<OperationLog> operationLogList = teamMembersService.addMember(CrmEnum.CUSTOMER, crmMemberSaveBO);
        return OperationResult.ok(operationLogList);
    }

    @PostMapping("/deleteMembers")
    @Operation(summary = "删除团队成员")
    @OperateLog(apply = ApplyEnum.CRM, object = OperateObjectEnum.CUSTOMER)
    public Result deleteMembers(@RequestBody CrmMemberSaveBO crmMemberSaveBO) {
        crmMemberSaveBO.getIds().forEach(id -> {
            boolean auth = AuthUtil.isPoolAuth(id, CrmAuthEnum.READ);
            if (auth) {
                throw new CrmException(SystemCodeEnum.SYSTEM_NO_AUTH);
            }
        });
        List<OperationLog> operationLogList = teamMembersService.deleteMember(CrmEnum.CUSTOMER, crmMemberSaveBO);
        return OperationResult.ok(operationLogList);
    }

    @PostMapping("/exitTeam/{customerId}")
    @Operation(summary = "删除团队成员")
    @OperateLog(apply = ApplyEnum.CRM, object = OperateObjectEnum.CUSTOMER)
    public Result exitTeam(@PathVariable("customerId") @Parameter(name = "客户ID") Long customerId) {
        OperationLog operationLog = teamMembersService.exitTeam(CrmEnum.CUSTOMER, customerId);
        return OperationResult.ok(Collections.singletonList(operationLog));
    }
}
</#if>
