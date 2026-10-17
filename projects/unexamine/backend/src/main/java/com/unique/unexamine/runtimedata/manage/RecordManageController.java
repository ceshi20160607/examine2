package com.unique.unexamine.runtimedata.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.unique.unexamine.platform.manage.AccountSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 已发布模块的基础记录表单、列表、详情和编辑接口。 */
@RestController
@RequestMapping("/api/systems/{systemId}/modules/{moduleId}/records")
public class RecordManageController {
    private final AccountSessionService sessions;
    private final RecordManageService records;

    public RecordManageController(AccountSessionService sessions, RecordManageService records) {
        this.sessions = sessions;
        this.records = records;
    }

    /** 按发布字段创建业务记录；额外字段与错误类型会被拒绝。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RecordManageService.RecordView create(@PathVariable Long systemId, @PathVariable Long moduleId,
                                                 @Valid @RequestBody ValuesRequest body, HttpServletRequest request) {
        return records.create(accountId(request), systemId, moduleId, body.values());
    }

    /** 分页查询当前账号有权查看的记录，可按标题或一个可搜索字段筛选。 */
    @GetMapping
    public RecordManageService.PageView list(@PathVariable Long systemId, @PathVariable Long moduleId,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size,
                                             @RequestParam(required = false) String search,
                                             @RequestParam(required = false) String filterKey,
                                             @RequestParam(required = false) String filterValue,
                                             HttpServletRequest request) {
        return records.list(accountId(request), systemId, moduleId, page, size, search, filterKey, filterValue);
    }

    /** 记录详情再次检查系统、主租户与负责人范围。 */
    @GetMapping("/{recordId}")
    public RecordManageService.RecordView detail(@PathVariable Long systemId, @PathVariable Long moduleId,
                                                 @PathVariable Long recordId, HttpServletRequest request) {
        return records.detail(accountId(request), systemId, moduleId, recordId);
    }

    /** 完整字段值编辑，必须提交读取时的版本号。 */
    @PutMapping("/{recordId}")
    public RecordManageService.RecordView update(@PathVariable Long systemId, @PathVariable Long moduleId,
                                                 @PathVariable Long recordId, @Valid @RequestBody UpdateRequest body,
                                                 HttpServletRequest request) {
        return records.update(accountId(request), systemId, moduleId, recordId, body.expectedVersion(), body.values());
    }

    private Long accountId(HttpServletRequest request) { return sessions.requireAccount(request).getId(); }

    /** 表单以稳定字段编码传值，服务端按当前发布快照校验。 */
    public record ValuesRequest(@NotNull Map<String, JsonNode> values) {}
    /** 乐观锁版本用于阻止静默覆盖。 */
    public record UpdateRequest(@NotNull @Min(0) Integer expectedVersion, @NotNull Map<String, JsonNode> values) {}
}
