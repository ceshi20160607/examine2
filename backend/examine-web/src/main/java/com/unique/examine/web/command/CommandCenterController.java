package com.unique.examine.web.command;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.web.command.CommandCenterModels.CommandCenterResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Role-aware command center API.
 */
@RestController
public class CommandCenterController {

    private final CommandCenterService commandCenterService;

    public CommandCenterController(CommandCenterService commandCenterService) {
        this.commandCenterService = commandCenterService;
    }

    @GetMapping("/api/v1/command-center")
    public ApiResponse<CommandCenterResponse> search(@RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) String systemId) {
        return ApiResponse.success(commandCenterService.search(keyword, systemId));
    }
}
