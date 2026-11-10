package com.unique.unexamine.sys.flow.manage.controller;

import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.sys.flow.manage.dto.ModuleStageDto.AttachRequest;
import com.unique.unexamine.sys.flow.manage.dto.ModuleStageDto.ConfigView;
import com.unique.unexamine.sys.flow.manage.dto.ModuleStageDto.RecordStageView;
import com.unique.unexamine.sys.flow.manage.dto.ModuleStageDto.RelatedRecord;
import com.unique.unexamine.sys.flow.manage.dto.ModuleStageDto.SaveRequest;
import com.unique.unexamine.sys.flow.manage.service.ModuleStageRecordService;
import com.unique.unexamine.sys.flow.manage.service.ModuleStageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 模块阶段配置和业务记录的补充资料关联入口。 */
@RestController
@RequestMapping("/api/systems/{systemId}/modules/{moduleId}")
@Tag(name="模块阶段")
public class ModuleStageController {
    private final ModuleStageService stages;
    private final ModuleStageRecordService records;

    public ModuleStageController(ModuleStageService stages,ModuleStageRecordService records) {
        this.stages=stages; this.records=records;
    }

    /** 查询模块当前阶段配置。 */
    @GetMapping("/stages")
    @Operation(summary="查询模块阶段配置")
    public Result<ConfigView> configuration(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @PathVariable Long systemId,@PathVariable Long moduleId) {
        return Result.ok(stages.configuration(token,systemId,tenantId,moduleId));
    }

    /** 保存阶段及其触发、审批和分支线路。 */
    @PutMapping("/stages")
    @Operation(summary="保存模块阶段配置")
    public Result<ConfigView> save(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @PathVariable Long systemId,@PathVariable Long moduleId,@RequestBody SaveRequest request) {
        return Result.ok(stages.save(token,systemId,tenantId,moduleId,request));
    }

    /** 详情页读取这条记录各 Flow 实例使用的阶段与补充记录。 */
    @GetMapping("/records/{recordId}/stages")
    @Operation(summary="查询记录阶段")
    public Result<List<RecordStageView>> forRecord(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @PathVariable Long systemId,@PathVariable Long moduleId,@PathVariable Long recordId) {
        return Result.ok(records.forRecord(token,systemId,tenantId,moduleId,recordId));
    }

    /** 当前阶段管理人员关联已有的补充模块记录。 */
    @PostMapping("/records/{recordId}/stages/{exampleId}/{stageCode}/related-records")
    @Operation(summary="关联阶段补充记录")
    public Result<RelatedRecord> attach(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @PathVariable Long systemId,@PathVariable Long moduleId,@PathVariable Long recordId,
            @PathVariable Long exampleId,@PathVariable String stageCode,@RequestBody AttachRequest request) {
        return Result.ok(records.attach(token,systemId,tenantId,moduleId,recordId,exampleId,stageCode,
                request==null?null:request.targetRecordId()));
    }

    /** 解除当前阶段的补充记录关联。 */
    @DeleteMapping("/records/{recordId}/stages/{exampleId}/{stageCode}/related-records/{relationId}")
    @Operation(summary="解除阶段补充记录")
    public Result<Void> detach(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @PathVariable Long systemId,@PathVariable Long moduleId,@PathVariable Long recordId,
            @PathVariable Long exampleId,@PathVariable String stageCode,@PathVariable Long relationId) {
        records.detach(token,systemId,tenantId,moduleId,recordId,exampleId,stageCode,relationId);
        return Result.ok();
    }
}
