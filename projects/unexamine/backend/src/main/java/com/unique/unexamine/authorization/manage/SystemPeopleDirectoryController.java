package com.unique.unexamine.authorization.manage;

import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.shared.manage.web.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system-directory")
public class SystemPeopleDirectoryController {
    private final SystemPeopleDirectoryService service;

    public SystemPeopleDirectoryController(SystemPeopleDirectoryService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResult<SystemPeopleDirectoryModels.Directory> directory(
            @RequestParam(required = false) String keyword) {
        return ApiResult.ok(service.directory(AuthenticationContextHolder.require(), keyword));
    }
}
