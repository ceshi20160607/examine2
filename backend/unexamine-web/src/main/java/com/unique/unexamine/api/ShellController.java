package com.unique.unexamine.api;

import com.unique.unexamine.core.auth.UserContext;
import com.unique.unexamine.core.shell.ShellSnapshot;
import com.unique.unexamine.service.AuthService;
import com.unique.unexamine.service.ShellSnapshotService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin(origins = {"http://127.0.0.1:5173", "http://localhost:5173", "null"}, allowedHeaders = "*")
@RestController
public class ShellController {
    private final ShellSnapshotService shellSnapshotService;
    private final AuthService authService;

    public ShellController(ShellSnapshotService shellSnapshotService, AuthService authService) {
        this.shellSnapshotService = shellSnapshotService;
        this.authService = authService;
    }

    @GetMapping("/api/shell")
    public ShellSnapshot shell(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "platform") String context
    ) {
        UserContext userContext = authService.currentContextOrDefault(authorization, context);
        return shellSnapshotService.snapshot(userContext.scope(), userContext.systemName());
    }
}