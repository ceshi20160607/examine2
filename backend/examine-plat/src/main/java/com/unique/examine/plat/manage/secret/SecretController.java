package com.unique.examine.plat.manage.secret;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.plat.manage.secret.SecretModels.SecretRefVO;
import com.unique.examine.plat.manage.secret.SecretModels.SecretRotationJobVO;
import com.unique.examine.plat.manage.secret.SecretModels.SecretRotationRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * SecretRef metadata and rotation API controller.
 */
@RestController
public class SecretController {

    private final SecretService secretService;

    public SecretController(SecretService secretService) {
        this.secretService = secretService;
    }

    @GetMapping("/api/v1/secrets/{secretRefId}")
    public ApiResponse<SecretRefVO> detail(@PathVariable String secretRefId) {
        return ApiResponse.success(secretService.detail(secretRefId));
    }

    @PostMapping("/api/v1/secrets/{secretRefId}/rotation-jobs")
    public ApiResponse<SecretRotationJobVO> createRotationJob(@PathVariable String secretRefId,
                                                              @RequestBody SecretRotationRequest request) {
        return ApiResponse.success(secretService.createRotationJob(secretRefId, request));
    }

    @GetMapping("/api/v1/secrets/rotation-jobs/{jobId}")
    public ApiResponse<SecretRotationJobVO> job(@PathVariable String jobId) {
        return ApiResponse.success(secretService.job(jobId));
    }
}
