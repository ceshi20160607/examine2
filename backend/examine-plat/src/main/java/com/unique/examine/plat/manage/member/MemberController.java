package com.unique.examine.plat.manage.member;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.PageRequest;
import com.unique.examine.core.api.PageResult;
import com.unique.examine.plat.manage.member.MemberModels.AccountBindingRequest;
import com.unique.examine.plat.manage.member.MemberModels.AccountBindingVO;
import com.unique.examine.plat.manage.member.MemberModels.MemberQueryRequest;
import com.unique.examine.plat.manage.member.MemberModels.MemberSaveRequest;
import com.unique.examine.plat.manage.member.MemberModels.MemberVO;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * System member API controller.
 */
@RestController
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping("/api/v1/systems/{systemId}/members")
    public ApiResponse<PageResult<MemberVO>> list(@PathVariable String systemId,
                                                  @RequestParam(defaultValue = "1") int pageNo,
                                                  @RequestParam(defaultValue = "20") int pageSize,
                                                  MemberQueryRequest query) {
        return ApiResponse.success(memberService.search(systemId, new PageRequest(pageNo, pageSize, null,
                List.of(), List.of()), query));
    }

    @PostMapping("/api/v1/systems/{systemId}/members")
    public ApiResponse<MemberVO> create(@PathVariable String systemId, @RequestBody MemberSaveRequest request) {
        return ApiResponse.success(memberService.create(systemId, request));
    }

    @PatchMapping("/api/v1/systems/{systemId}/members/{systemMemberId}")
    public ApiResponse<MemberVO> update(@PathVariable String systemId, @PathVariable String systemMemberId,
                                        @RequestBody MemberSaveRequest request) {
        return ApiResponse.success(memberService.update(systemId, systemMemberId, request));
    }

    @PostMapping("/api/v1/systems/{systemId}/members/{systemMemberId}/bind-account")
    public ApiResponse<AccountBindingVO> bindAccount(@PathVariable String systemId, @PathVariable String systemMemberId,
                                                     @RequestBody AccountBindingRequest request) {
        return ApiResponse.success(memberService.bindAccount(systemId, systemMemberId, request));
    }

    @GetMapping("/api/v1/systems/{systemId}/member-bindings")
    public ApiResponse<List<AccountBindingVO>> bindings(@PathVariable String systemId) {
        return ApiResponse.success(memberService.bindings(systemId));
    }
}
