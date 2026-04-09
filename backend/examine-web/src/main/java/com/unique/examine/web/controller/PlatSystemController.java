package com.unique.examine.web.controller;

import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.plat.entity.PlatSystem;
import com.unique.examine.plat.service.PlatSystemService;
import com.unique.examine.web.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "自建系统")
@RestController
@RequestMapping("/api/v1/platform/systems")
public class PlatSystemController {

    private final PlatSystemService platSystemService;

    public PlatSystemController(PlatSystemService platSystemService) {
        this.platSystemService = platSystemService;
    }

    @Operation(summary = "我创建的系统（不含 id=0 平台占位）")
    @GetMapping
    public ApiResult<List<PlatSystem>> mine() {
        return ApiResult.ok(platSystemService.listVisibleForUser(AuthContextHolder.getPlatId()));
    }

    @Operation(summary = "详情")
    @GetMapping("/{id}")
    public ApiResult<PlatSystem> get(@PathVariable Long id) {
        return ApiResult.ok(platSystemService.getRequired(id));
    }

    @Operation(summary = "创建自建系统")
    @PostMapping
    public ApiResult<PlatSystem> create(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        return ApiResult.ok(platSystemService.createSystem(AuthContextHolder.getPlatId(), name));
    }
}
