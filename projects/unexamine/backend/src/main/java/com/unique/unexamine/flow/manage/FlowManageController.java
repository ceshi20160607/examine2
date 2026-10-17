package com.unique.unexamine.flow.manage;

import com.unique.unexamine.platform.manage.AccountSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 同一系统业务模块的审批配置、发起、待办和办理接口。 */
@RestController
@RequestMapping("/api/systems/{systemId}")
public class FlowManageController {
    private final AccountSessionService sessions;
    private final FlowManageService flows;

    public FlowManageController(AccountSessionService sessions, FlowManageService flows) {
        this.sessions = sessions;
        this.flows = flows;
    }

    /** 所属人读取某模块的草稿和当前发布版本。 */
    @GetMapping("/modules/{moduleId}/approval")
    public ResponseEntity<FlowManageService.FlowConfig> config(@PathVariable Long systemId, @PathVariable Long moduleId,
                                                               HttpServletRequest request) {
        FlowManageService.FlowConfig config = flows.config(accountId(request), systemId, moduleId);
        return config == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(config);
    }

    /** 设置一个指定成员审批节点的草稿。 */
    @PutMapping("/modules/{moduleId}/approval")
    public FlowManageService.FlowConfig draft(@PathVariable Long systemId, @PathVariable Long moduleId,
                                              @Valid @RequestBody DraftRequest body, HttpServletRequest request) {
        return flows.saveDraft(accountId(request), systemId, moduleId,
                body.name(), body.nodeName(), body.approverMemberId());
    }

    /** 发布审批版本后新实例才会采用该版本。 */
    @PostMapping("/modules/{moduleId}/approval/publish")
    public FlowManageService.FlowConfig publish(@PathVariable Long systemId, @PathVariable Long moduleId,
                                                HttpServletRequest request) {
        return flows.publish(accountId(request), systemId, moduleId);
    }

    /** 记录负责人手动发起已发布的审批。 */
    @PostMapping("/modules/{moduleId}/records/{recordId}/submit-approval")
    @ResponseStatus(HttpStatus.CREATED)
    public FlowManageService.FlowRun submit(@PathVariable Long systemId, @PathVariable Long moduleId,
                                            @PathVariable Long recordId, HttpServletRequest request) {
        return flows.submit(accountId(request), systemId, moduleId, recordId);
    }

    /** 列出当前成员有权办理的待办。 */
    @GetMapping("/approval/tasks")
    public List<FlowManageService.FlowRun> todos(@PathVariable Long systemId, HttpServletRequest request) {
        return flows.todos(accountId(request), systemId);
    }

    /** 待办详情展示任务对应业务记录及历史。 */
    @GetMapping("/approval/tasks/{taskId}")
    public FlowManageService.TaskDetail detail(@PathVariable Long systemId, @PathVariable Long taskId,
                                               HttpServletRequest request) {
        return flows.detail(accountId(request), systemId, taskId);
    }

    /** 决策与记录状态回写在一个数据库事务中完成。 */
    @PostMapping("/approval/tasks/{taskId}/decision")
    public FlowManageService.FlowRun decide(@PathVariable Long systemId, @PathVariable Long taskId,
                                            @Valid @RequestBody DecisionRequest body, HttpServletRequest request) {
        return flows.decide(accountId(request), systemId, taskId, body.approve(), body.opinion());
    }

    private Long accountId(HttpServletRequest request) { return sessions.requireAccount(request).getId(); }

    /** 当前基础审批只配置一个指定成员节点，后续版本可扩展节点类型。 */
    public record DraftRequest(@NotBlank String name, @NotBlank String nodeName, @NotNull Long approverMemberId) {}
    /** 拒绝原因由业务服务校验为必填。 */
    public record DecisionRequest(@NotNull Boolean approve, String opinion) {}
}
