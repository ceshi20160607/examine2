package com.unique.examine.file.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.id.IdService;
import com.unique.examine.core.api.ApprovalEvidenceFileFacade;
import com.unique.examine.core.api.IdempotencyFacade;
import com.unique.examine.core.api.OperationAuditFacade;
import com.unique.examine.core.api.OutboxFacade;
import com.unique.examine.core.runtime.RuntimeRecordAccessFacade;
import com.unique.examine.file.adapter.jdbc.JdbcFileAssetRepository;
import com.unique.examine.file.port.FileAssetRepository;
import com.unique.examine.file.port.FileContentStore;
import com.unique.examine.file.port.FileStorageStatusProvider;
import com.unique.examine.file.openapi.OpenApiRecordFileFacade;
import com.unique.examine.file.service.FileAssetService;
import com.unique.examine.file.service.ApprovalEvidenceFileAdapter;
import com.unique.examine.file.service.RuntimeRecordFileService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class FileJdbcConfiguration {
    @Bean
    FileAssetRepository fileAssetRepository(JdbcTemplate jdbcTemplate, IdService idService) {
        return new JdbcFileAssetRepository(jdbcTemplate, idService);
    }

    @Bean
    FileAssetService fileAssetService(
            FileAssetRepository repository,
            FileContentStore contentStore,
            FileStorageStatusProvider storageStatus
    ) {
        return new FileAssetService(repository, contentStore, storageStatus, Clock.systemUTC());
    }

    @Bean
    ApprovalEvidenceFileFacade approvalEvidenceFileFacade(
            FileAssetService service
    ) {
        return new ApprovalEvidenceFileAdapter(service);
    }

    @Bean
    RuntimeRecordFileService runtimeRecordFileService(
            FileAssetService service,
            FileAssetRepository repository,
            RuntimeRecordAccessFacade recordAccess
    ) {
        return new RuntimeRecordFileService(service, repository, recordAccess);
    }

    @Bean
    OpenApiRecordFileFacade openApiRecordFileFacade(
            RuntimeRecordFileService files,
            IdempotencyFacade idempotency,
            OperationAuditFacade audits,
            OutboxFacade outbox,
            ObjectMapper objectMapper
    ) {
        return new OpenApiRecordFileFacade(files, idempotency, audits, outbox, objectMapper);
    }
}
