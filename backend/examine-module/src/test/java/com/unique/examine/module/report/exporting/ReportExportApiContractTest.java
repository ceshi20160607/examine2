package com.unique.examine.module.report.exporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.module.runtime.security.RuntimeSession;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportExportApiContractTest {
    @Test
    void freezesManualExportRoutesHeaderAndTransactionBoundaries()
            throws Exception {
        var root = ReportExportController.class.getAnnotation(
                RequestMapping.class);
        var start = ReportExportController.class.getMethod("start",
                long.class, String.class, String.class,
                ReportExportViews.StartRequest.class, Object.class,
                HttpServletRequest.class);
        var idempotency = start.getParameters()[2]
                .getAnnotation(RequestHeader.class);
        var result = ReportExportController.class.getMethod("result",
                long.class, String.class, long.class, Object.class)
                .getAnnotation(GetMapping.class);
        var serviceStart = ReportExportService.class.getMethod("start",
                RuntimeSession.class, String.class,
                ReportExportViews.StartRequest.class, String.class,
                String.class, String.class);

        assertThat(root.value()).containsExactly(
                "/api/v1/systems/{systemId}/reports/{reportCode}/exports");
        assertThat(idempotency.name()).isEqualTo("Idempotency-Key");
        assertThat(result.value()).containsExactly(
                "/{exportId}/result.xlsx");
        assertThat(serviceStart.getAnnotation(Transactional.class))
                .isNotNull();
    }

    @Test
    void freezesTaskJsonShapeWithStringIdentifiers() throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var json = mapper.readTree(
                mapper.writeValueAsString(
                        new ReportExportViews.Task(
                                "9007199254740994", "ops_report",
                                "9007199254740995", 2,
                                "9007199254740996", List.of("status"),
                                "SUCCEEDED", 5_001L, 5_000, true,
                                "9007199254740997", "ops.xlsx", 42L,
                                null, null, null, null, null)));

        assertThat(json.fieldNames()).toIterable().containsExactly(
                "exportId", "reportCode", "reportVersionId",
                "reportVersionNumber", "dataSourceVersionId",
                "fieldCodes", "status", "totalRows", "processedRows",
                "truncated", "jobId", "resultFilename", "resultSize",
                "failureCode", "failureMessage", "createdAt", "startedAt",
                "finishedAt");
        assertThat(json.path("exportId").asText())
                .isEqualTo("9007199254740994");
        assertThat(json.path("reportVersionId").asText())
                .isEqualTo("9007199254740995");
        assertThat(json.path("truncated").asBoolean()).isTrue();
    }
}
