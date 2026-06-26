package com.unique.examine.plat.manage.org;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.plat.manage.org.OrgModels.DepartmentSaveRequest;
import com.unique.examine.plat.manage.org.OrgModels.DepartmentVO;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Organization API controller.
 */
@RestController
public class OrgController {

    private final OrgService orgService;

    public OrgController(OrgService orgService) {
        this.orgService = orgService;
    }

    @GetMapping("/api/v1/systems/{systemId}/org/departments")
    public ApiResponse<List<DepartmentVO>> departmentTree(@PathVariable String systemId) {
        return ApiResponse.success(orgService.departmentTree(systemId));
    }

    @PostMapping("/api/v1/systems/{systemId}/org/departments")
    public ApiResponse<DepartmentVO> createDepartment(@PathVariable String systemId,
                                                      @RequestBody DepartmentSaveRequest request) {
        return ApiResponse.success(orgService.createDepartment(systemId, request));
    }
}
