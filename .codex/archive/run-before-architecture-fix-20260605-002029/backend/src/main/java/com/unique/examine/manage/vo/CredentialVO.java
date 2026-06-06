package com.unique.examine.manage.vo;

import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "OpenAPI凭证出参")
public class CredentialVO {
    private Long id;
    private Long clientPk;
    private Integer keyVersion;
    private String secretOnce;
    private String status;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}

