package com.unique.unexamine.api;

import com.unique.unexamine.core.admin.AdminConfigItem;
import com.unique.unexamine.core.admin.AdminConfigUpdateRequest;
import com.unique.unexamine.service.AdminConfigService;
import com.unique.unexamine.service.AuthService;
import java.util.List;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin(origins = {"http://127.0.0.1:5173", "http://localhost:5173", "null"}, allowedHeaders = "*")
@RestController
public class AdminConfigController {
    private final AdminConfigService adminConfigService;
    private final AuthService authService;

    public AdminConfigController(AdminConfigService adminConfigService, AuthService authService) {
        this.adminConfigService = adminConfigService;
        this.authService = authService;
    }

    @GetMapping("/api/admin/config")
    public List<AdminConfigItem> list(@RequestHeader(name = "Authorization", required = false) String authorization) {
        authService.profile(authorization);
        return adminConfigService.list();
    }

    @GetMapping("/api/admin/config/{code}")
    public AdminConfigItem get(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String code
    ) {
        authService.profile(authorization);
        return adminConfigService.get(code);
    }

    @PatchMapping("/api/admin/config/{code}")
    public AdminConfigItem update(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String code,
            @RequestBody AdminConfigUpdateRequest request
    ) {
        authService.profile(authorization);
        return adminConfigService.update(code, request);
    }
}