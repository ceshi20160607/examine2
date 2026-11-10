package com.unique.unexamine.sys.module.manage.service;

import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.sys.manage.service.SystemManageService;
import com.unique.unexamine.sys.module.base.service.IUnModuleExchangeJobService;
import org.springframework.stereotype.Service;

/** 消息只保存作业主键；打开结果前解析模块并复用原作业的读取授权。 */
@Service
public class ModuleExchangeTargetService {
    private final IUnModuleExchangeJobService jobs;
    private final ModuleImportService imports;
    private final ModuleExportJobService exports;
    private final SystemManageService systems;

    public ModuleExchangeTargetService(IUnModuleExchangeJobService jobs, ModuleImportService imports,
            ModuleExportJobService exports, SystemManageService systems) {
        this.jobs=jobs; this.imports=imports; this.exports=exports; this.systems=systems;
    }

    /** 仅向有权读取作业结果的当前系统租户成员返回跳转所需模块编号。 */
    public TargetView resolve(String token, Long systemId, Long tenantId, Long jobId) {
        var context=systems.context(token,systemId,tenantId);
        if (jobId==null || jobId<1) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"作业主键无效");
        var row=jobs.getById(jobId);
        if (row==null || !context.systemId().equals(row.getSystemId())
                || !context.tenantId().equals(row.getTenantId()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,"数据交换作业不存在");
        if ("IMPORT".equals(row.getJobType())) {
            var result=imports.job(token,systemId,tenantId,row.getModuleId(),jobId);
            return new TargetView(jobId,row.getModuleId(),"IMPORT",result.status());
        }
        if ("EXPORT".equals(row.getJobType())) {
            var result=exports.job(token,systemId,tenantId,row.getModuleId(),jobId);
            return new TargetView(jobId,row.getModuleId(),"EXPORT",result.status());
        }
        throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,"数据交换作业不存在");
    }

    /** 前端据此进入对应模块的导入或导出结果页。 */
    public record TargetView(Long jobId, Long moduleId, String jobType, String status) {}
}
