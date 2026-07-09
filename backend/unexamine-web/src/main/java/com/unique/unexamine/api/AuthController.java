package com.unique.unexamine.api;

import com.unique.unexamine.core.auth.LoginRequest;
import com.unique.unexamine.core.auth.LoginResponse;
import com.unique.unexamine.core.auth.SwitchContextRequest;
import com.unique.unexamine.core.auth.UserContext;
import com.unique.unexamine.service.AuthService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin(origins = {"http://127.0.0.1:5173", "http://localhost:5173", "null"}, allowedHeaders = "*")
@RestController
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/api/auth/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/api/auth/profile")
    public LoginResponse profile(@RequestHeader(name = "Authorization", required = false) String authorization) {
        return authService.profile(authorization);
    }

    @PostMapping("/api/context/switch")
    public UserContext switchContext(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestBody SwitchContextRequest request
    ) {
        return authService.switchContext(authorization, request);
    }

    @PostMapping("/api/auth/logout")
    public void logout(@RequestHeader(name = "Authorization", required = false) String authorization) {
        authService.logout(authorization);
    }
}