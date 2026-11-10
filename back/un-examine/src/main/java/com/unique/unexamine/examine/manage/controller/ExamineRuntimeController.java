package com.unique.unexamine.examine.manage.controller;

import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.examine.manage.dto.ExamineRuntimeDto.ActionRequest;
import com.unique.unexamine.examine.manage.dto.ExamineRuntimeDto.ActionView;
import com.unique.unexamine.examine.manage.dto.ExamineRuntimeDto.ExampleView;
import com.unique.unexamine.examine.manage.service.ExamineRuntimeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 业务记录详情页使用的审批实例和节点办理入口。 */
@RestController
@RequestMapping("/api/systems/{systemId}/modules/{moduleId}/records/{recordId}/examines")
@Tag(name = "模块审批运行")
public class ExamineRuntimeController {
    private final ExamineRuntimeService service;

    public ExamineRuntimeController(ExamineRuntimeService service) {
        this.service = service;
    }

    /** 查询记录关联的审批实例和节点处理历史。 */
    @GetMapping
    @Operation(summary = "查询记录审批")
    public Result<List<ExampleView>> list(@RequestHeader(value = "Authorization", required = false) String token,
                                          @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
                                          @PathVariable Long systemId, @PathVariable Long moduleId,
                                          @PathVariable Long recordId) {
        return Result.ok(service.listForRecord(token, systemId, tenantId, moduleId, recordId));
    }

    /** 查询一个审批实例。 */
    @GetMapping("/{exampleId}")
    @Operation(summary = "查询审批实例")
    public Result<ExampleView> query(@RequestHeader(value = "Authorization", required = false) String token,
                                     @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
                                     @PathVariable Long systemId, @PathVariable Long moduleId,
                                     @PathVariable Long recordId, @PathVariable Long exampleId) {
        return Result.ok(service.query(token, systemId, tenantId, moduleId, recordId, exampleId));
    }

    /** 办理当前审批节点；请求号保证重试不会重复处理。 */
    @PostMapping("/{exampleId}/nodes/{nodeId}/actions")
    @Operation(summary = "办理审批节点")
    public Result<ActionView> action(@RequestHeader(value = "Authorization", required = false) String token,
                                     @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
                                     @PathVariable Long systemId, @PathVariable Long moduleId,
                                     @PathVariable Long recordId, @PathVariable Long exampleId,
                                     @PathVariable Long nodeId, @Valid @RequestBody ActionRequest request) {
        return Result.ok(service.action(token, systemId, tenantId, moduleId, recordId, exampleId, nodeId, request));
    }
}
