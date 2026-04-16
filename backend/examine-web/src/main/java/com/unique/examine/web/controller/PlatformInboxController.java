package com.unique.examine.web.controller;

import com.unique.examine.core.web.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 平台级消息 / 待办占位（README 阶段 A-4）：后续按 systemId、租户与 AuthContext 过滤，对接独立表或服务。
 */
@Tag(name = "平台态-消息与待办（占位）")
@RestController
@RequestMapping("/v1/platform")
public class PlatformInboxController {

    @Operation(summary = "平台消息列表（占位：空列表；后续多系统聚合与权限过滤）")
    @GetMapping("/messages")
    public ApiResult<List<Map<String, Object>>> messages() {
        return ApiResult.ok(Collections.emptyList());
    }

    @Operation(summary = "平台待办列表（占位：空列表；后续多系统聚合与权限过滤）")
    @GetMapping("/todos")
    public ApiResult<List<Map<String, Object>>> todos() {
        return ApiResult.ok(Collections.emptyList());
    }
}
