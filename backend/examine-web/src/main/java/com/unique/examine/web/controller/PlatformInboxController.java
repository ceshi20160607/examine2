package com.unique.examine.web.controller;

import com.unique.examine.core.web.ApiResult;
import com.unique.examine.flow.entity.po.FlowTask;
import com.unique.examine.plat.entity.po.PlatSystem;
import com.unique.examine.plat.entity.po.PlatMsg;
import com.unique.examine.plat.service.IPlatMsgService;
import com.unique.examine.plat.service.IPlatSystemService;
import com.unique.examine.web.service.FlowTaskInboxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 平台级消息 / 待办占位（README 阶段 A-4）：后续按 systemId、租户与 AuthContext 过滤，对接独立表或服务。
 */
@Tag(name = "平台态-消息与待办（占位）")
@RestController
@RequestMapping("/v1/platform")
public class PlatformInboxController {

    @Autowired
    private IPlatMsgService platMsgService;

    @Autowired
    private IPlatSystemService platSystemService;

    @Autowired
    private FlowTaskInboxService flowTaskInboxService;

    @Operation(summary = "平台消息列表（MVP：查询 un_plat_msg；后续多系统聚合与权限过滤）")
    @GetMapping("/messages")
    public ApiResult<List<PlatMsg>> messages(
            @RequestParam(name = "limit", defaultValue = "50") int limit
    ) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        LocalDateTime now = LocalDateTime.now();

        List<PlatMsg> list = platMsgService.lambdaQuery()
                .eq(PlatMsg::getStatus, 1)
                .and(w -> w.isNull(PlatMsg::getPublishTime).or().le(PlatMsg::getPublishTime, now))
                .and(w -> w.isNull(PlatMsg::getExpireTime).or().gt(PlatMsg::getExpireTime, now))
                .orderByDesc(PlatMsg::getPublishTime)
                .orderByDesc(PlatMsg::getCreateTime)
                .last("limit " + safeLimit)
                .list();

        return ApiResult.ok(list);
    }

    @Operation(summary = "平台待办列表（MVP：聚合 flow 待办；后续多系统权限过滤）")
    @GetMapping("/todos")
    public ApiResult<List<FlowTask>> todos(
            @RequestParam(name = "limit", defaultValue = "50") int limit,
            @RequestParam(name = "systemId", required = false) Long systemId,
            @RequestParam(name = "tenantId", required = false) Long tenantId
    ) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        Set<Long> allowed = listMyAllowedSystemIds();
        return ApiResult.ok(flowTaskInboxService.listMyPendingTasksAcrossSystems(safeLimit, systemId, tenantId, allowed));
    }

    @Operation(summary = "平台抄送列表（cc；onlyUnread=1 仅未读；后续多系统权限过滤）")
    @GetMapping("/cc")
    public ApiResult<List<FlowTask>> cc(
            @RequestParam(name = "limit", defaultValue = "50") int limit,
            @RequestParam(name = "onlyUnread", required = false) Integer onlyUnread,
            @RequestParam(name = "systemId", required = false) Long systemId,
            @RequestParam(name = "tenantId", required = false) Long tenantId
    ) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        Set<Long> allowed = listMyAllowedSystemIds();
        return ApiResult.ok(flowTaskInboxService.listMyCcTasksAcrossSystems(safeLimit, onlyUnread, systemId, tenantId, allowed));
    }

    @Operation(summary = "平台抄送标记已读（cc）")
    @org.springframework.web.bind.annotation.PostMapping("/cc/{taskId}/read")
    public ApiResult<Void> readCc(@org.springframework.web.bind.annotation.PathVariable("taskId") Long taskId) {
        flowTaskInboxService.markMyCcReadAcrossSystems(taskId);
        return ApiResult.ok();
    }

    private Set<Long> listMyAllowedSystemIds() {
        // MVP：系统权限模型是“系统所有者可进入/可见”。
        Long platId = com.unique.examine.core.security.AuthContextHolder.getPlatId();
        if (platId == null) {
            return Set.of();
        }

        List<PlatSystem> systems = platSystemService.lambdaQuery()
                .eq(PlatSystem::getOwnerPlatAccountId, platId)
                .eq(PlatSystem::getStatus, 1)
                .list();
        if (systems == null || systems.isEmpty()) {
            return Set.of();
        }
        return systems.stream()
                .map(PlatSystem::getId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
    }
}
