package com.unique.examine.web.controller;

import com.unique.examine.core.web.ApiResult;
import com.unique.examine.plat.entity.po.PlatMsg;
import com.unique.examine.plat.service.IPlatMsgService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

/**
 * 平台级消息 / 待办占位（README 阶段 A-4）：后续按 systemId、租户与 AuthContext 过滤，对接独立表或服务。
 */
@Tag(name = "平台态-消息与待办（占位）")
@RestController
@RequestMapping("/v1/platform")
public class PlatformInboxController {

    @Autowired
    private IPlatMsgService platMsgService;

    @Operation(summary = "平台消息列表（MVP：查询 un_plat_msg；后续多系统聚合与权限过滤）")
    @GetMapping("/messages")
    public ApiResult<List<PlatMsg>> messages(
            @RequestParam(name = "limit", defaultValue = "50") int limit
    ) {
        int safeLimit = Math.max(1, Math.min(limit, 200));

        List<PlatMsg> list = platMsgService.lambdaQuery()
                .eq(PlatMsg::getStatus, 1)
                .orderByDesc(PlatMsg::getPublishTime)
                .orderByDesc(PlatMsg::getCreateTime)
                .last("limit " + safeLimit)
                .list();

        return ApiResult.ok(list);
    }

    @Operation(summary = "平台待办列表（占位：空列表；后续多系统聚合与权限过滤）")
    @GetMapping("/todos")
    public ApiResult<List<Object>> todos() {
        return ApiResult.ok(Collections.emptyList());
    }
}
