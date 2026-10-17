package com.unique.unexamine.runtimedata.manage;

import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 记录详情的评论入口；每次读取和写入都重新校验当前记录权限。 */
@RestController
@RequestMapping("/api/runtime/modules/{moduleCode}/records/{recordId}/comments")
public class RuntimeRecordCommentController {
    private final RuntimeRecordCommentService commentService;

    public RuntimeRecordCommentController(RuntimeRecordCommentService commentService) {
        this.commentService = commentService;
    }

    /** 共享接收方只读可见来源记录的评论，撤销共享后详情校验会立即拒绝。 */
    @GetMapping
    public ApiResult<RuntimeRecordCommentModels.Page> list(
            @PathVariable String moduleCode, @PathVariable Long recordId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        return ApiResult.ok(commentService.list(AuthenticationContextHolder.require(), moduleCode, recordId,
                page, pageSize, TraceIdFilter.current(request)));
    }

    /** 来源成员发表评论或回复；作者、租户和模块归属均从可信上下文和真实记录取得。 */
    @PostMapping
    public ApiResult<RuntimeRecordCommentModels.View> create(
            @PathVariable String moduleCode, @PathVariable Long recordId,
            @Valid @RequestBody RuntimeRecordCommentModels.CreateRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(commentService.create(AuthenticationContextHolder.require(), moduleCode, recordId,
                body, TraceIdFilter.current(request)));
    }
}
