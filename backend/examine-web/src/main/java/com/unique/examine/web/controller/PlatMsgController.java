package com.unique.examine.web.controller;

import com.unique.examine.plat.entity.PlatMsg;
import com.unique.examine.plat.service.PlatMsgService;
import com.unique.examine.web.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "平台消息")
@RestController
@RequestMapping("/api/v1/platform/messages")
public class PlatMsgController {

    private final PlatMsgService platMsgService;

    public PlatMsgController(PlatMsgService platMsgService) {
        this.platMsgService = platMsgService;
    }

    @Operation(summary = "列表")
    @GetMapping
    public ApiResult<List<PlatMsg>> list() {
        return ApiResult.ok(platMsgService.list());
    }

    @Operation(summary = "详情")
    @GetMapping("/{id}")
    public ApiResult<PlatMsg> get(@PathVariable Long id) {
        return ApiResult.ok(platMsgService.getById(id));
    }

    @Operation(summary = "新增")
    @PostMapping
    public ApiResult<Void> create(@RequestBody PlatMsg body) {
        platMsgService.saveOrUpdateMsg(body);
        return ApiResult.ok();
    }

    @Operation(summary = "更新")
    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable Long id, @RequestBody PlatMsg body) {
        body.setId(id);
        platMsgService.saveOrUpdateMsg(body);
        return ApiResult.ok();
    }

    @Operation(summary = "删除")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        platMsgService.removeById(id);
        return ApiResult.ok();
    }
}
