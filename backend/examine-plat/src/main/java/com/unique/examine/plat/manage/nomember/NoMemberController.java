package com.unique.examine.plat.manage.nomember;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.PageRequest;
import com.unique.examine.core.api.PageResult;
import com.unique.examine.plat.manage.nomember.NoMemberModels.NoMemberAccessRequestCreateRequest;
import com.unique.examine.plat.manage.nomember.NoMemberModels.NoMemberAccessRequestQuery;
import com.unique.examine.plat.manage.nomember.NoMemberModels.NoMemberAccessRequestVO;
import com.unique.examine.plat.manage.nomember.NoMemberModels.NoMemberApproveRequest;
import com.unique.examine.plat.manage.nomember.NoMemberModels.NoMemberRejectRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * No-member access request API controller.
 */
@RestController
public class NoMemberController {

    private final NoMemberService noMemberService;

    public NoMemberController(NoMemberService noMemberService) {
        this.noMemberService = noMemberService;
    }

    @PostMapping("/api/v1/systems/{systemId}/no-member-access-requests")
    public ApiResponse<NoMemberAccessRequestVO> create(@PathVariable String systemId,
                                                       @RequestBody NoMemberAccessRequestCreateRequest request) {
        return ApiResponse.success(noMemberService.create(systemId, request));
    }

    @GetMapping("/api/v1/systems/{systemId}/no-member-access-requests")
    public ApiResponse<PageResult<NoMemberAccessRequestVO>> list(@PathVariable String systemId,
                                                                 @RequestParam(defaultValue = "1") int pageNo,
                                                                 @RequestParam(defaultValue = "20") int pageSize,
                                                                 NoMemberAccessRequestQuery query) {
        return ApiResponse.success(noMemberService.search(systemId, new PageRequest(pageNo, pageSize, null,
                List.of(), List.of()), query));
    }

    @PostMapping("/api/v1/systems/{systemId}/no-member-access-requests/{requestId}/approve")
    public ApiResponse<NoMemberAccessRequestVO> approve(@PathVariable String systemId,
                                                        @PathVariable String requestId,
                                                        @RequestBody NoMemberApproveRequest request) {
        return ApiResponse.success(noMemberService.approve(systemId, requestId, request));
    }

    @PostMapping("/api/v1/systems/{systemId}/no-member-access-requests/{requestId}/reject")
    public ApiResponse<NoMemberAccessRequestVO> reject(@PathVariable String systemId,
                                                       @PathVariable String requestId,
                                                       @RequestBody NoMemberRejectRequest request) {
        return ApiResponse.success(noMemberService.reject(systemId, requestId, request));
    }
}
