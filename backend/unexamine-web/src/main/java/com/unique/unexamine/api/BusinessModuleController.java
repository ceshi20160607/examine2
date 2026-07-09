package com.unique.unexamine.api;

import com.unique.unexamine.core.module.BusinessModuleConfig;
import com.unique.unexamine.core.module.RecordCreateRequest;
import com.unique.unexamine.core.module.RuntimeRecord;
import com.unique.unexamine.service.AuthService;
import com.unique.unexamine.service.BusinessModuleService;
import java.util.List;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin(origins = {"http://127.0.0.1:5173", "http://localhost:5173", "null"}, allowedHeaders = "*")
@RestController
public class BusinessModuleController {
    private final BusinessModuleService businessModuleService;
    private final AuthService authService;

    public BusinessModuleController(BusinessModuleService businessModuleService, AuthService authService) {
        this.businessModuleService = businessModuleService;
        this.authService = authService;
    }

    @GetMapping("/api/business/modules")
    public List<BusinessModuleConfig> modules(@RequestHeader(name = "Authorization", required = false) String authorization) {
        authService.profile(authorization);
        return businessModuleService.modules();
    }

    @GetMapping("/api/business/modules/{moduleCode}")
    public BusinessModuleConfig module(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String moduleCode
    ) {
        authService.profile(authorization);
        return businessModuleService.module(moduleCode);
    }

    @GetMapping("/api/business/modules/{moduleCode}/records")
    public List<RuntimeRecord> records(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String moduleCode
    ) {
        authService.profile(authorization);
        return businessModuleService.records(moduleCode);
    }

    @GetMapping("/api/business/modules/{moduleCode}/records/{recordId}")
    public RuntimeRecord record(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String moduleCode,
            @PathVariable String recordId
    ) {
        authService.profile(authorization);
        return businessModuleService.record(moduleCode, recordId);
    }

    @PostMapping("/api/business/modules/{moduleCode}/records")
    public RuntimeRecord create(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String moduleCode,
            @RequestBody RecordCreateRequest request
    ) {
        authService.profile(authorization);
        return businessModuleService.create(moduleCode, request);
    }
}