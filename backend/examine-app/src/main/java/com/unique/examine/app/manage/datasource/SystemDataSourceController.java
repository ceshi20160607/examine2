package com.unique.examine.app.manage.datasource;

import com.unique.examine.app.manage.datasource.SystemDataSourceModels.DataSourceCheckResult;
import com.unique.examine.app.manage.datasource.SystemDataSourceModels.DataSourceQuery;
import com.unique.examine.app.manage.datasource.SystemDataSourceModels.DataSourceSaveRequest;
import com.unique.examine.app.manage.datasource.SystemDataSourceModels.DataSourceVO;
import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.PageRequest;
import com.unique.examine.core.api.PageResult;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SystemDataSourceController {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final SystemDataSourceService dataSourceService;

    public SystemDataSourceController(SystemDataSourceService dataSourceService) {
        this.dataSourceService = dataSourceService;
    }

    @GetMapping("/api/v1/systems/{systemId}/data-sources")
    public ApiResponse<PageResult<DataSourceVO>> list(@PathVariable String systemId,
                                                      @RequestParam(defaultValue = "1") int pageNo,
                                                      @RequestParam(defaultValue = "20") int pageSize,
                                                      DataSourceQuery query) {
        return ApiResponse.success(dataSourceService.list(systemId,
                new PageRequest(pageNo, pageSize, null, List.of(), List.of()), query));
    }

    @PostMapping("/api/v1/systems/{systemId}/data-sources")
    public ApiResponse<DataSourceVO> create(@PathVariable String systemId,
                                            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false)
                                            String idempotencyKey,
                                            @RequestBody DataSourceSaveRequest request) {
        return ApiResponse.success(dataSourceService.create(systemId, idempotencyKey, request));
    }

    @GetMapping("/api/v1/systems/{systemId}/data-sources/{dataSourceId}")
    public ApiResponse<DataSourceVO> detail(@PathVariable String systemId,
                                            @PathVariable String dataSourceId) {
        return ApiResponse.success(dataSourceService.detail(systemId, dataSourceId));
    }

    @PatchMapping("/api/v1/systems/{systemId}/data-sources/{dataSourceId}")
    public ApiResponse<DataSourceVO> update(@PathVariable String systemId,
                                            @PathVariable String dataSourceId,
                                            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false)
                                            String idempotencyKey,
                                            @RequestBody DataSourceSaveRequest request) {
        return ApiResponse.success(dataSourceService.update(systemId, dataSourceId, idempotencyKey, request));
    }

    @PostMapping("/api/v1/systems/{systemId}/data-sources/{dataSourceId}/connection-check")
    public ApiResponse<DataSourceCheckResult> connectionCheck(@PathVariable String systemId,
                                                              @PathVariable String dataSourceId) {
        return ApiResponse.success(dataSourceService.connectionCheck(systemId, dataSourceId));
    }

    @PostMapping("/api/v1/systems/{systemId}/data-sources/{dataSourceId}/publish-check")
    public ApiResponse<DataSourceCheckResult> publishCheck(@PathVariable String systemId,
                                                           @PathVariable String dataSourceId) {
        return ApiResponse.success(dataSourceService.publishCheck(systemId, dataSourceId));
    }
}
