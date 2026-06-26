package com.unique.examine.flow.manage.definition;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.PageRequest;
import com.unique.examine.core.api.PageResult;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.CanvasSaveRequest;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.CanvasVO;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.FlowDefinitionVO;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.FlowQueryRequest;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.FlowSaveRequest;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.FlowSimulationRequest;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.FlowSimulationResult;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.FlowSnapshotVO;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.ImpactAnalysisVO;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.NodeLibraryItem;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.NodePropertyPanelVO;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.PublishCheckResultVO;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.PublishRequest;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.PublishResult;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Flow definition and configuration API controller.
 */
@RestController
public class FlowDefinitionController {

    private final FlowDefinitionService flowDefinitionService;

    public FlowDefinitionController(FlowDefinitionService flowDefinitionService) {
        this.flowDefinitionService = flowDefinitionService;
    }

    @GetMapping("/api/v1/systems/{systemId}/flows")
    public ApiResponse<PageResult<FlowDefinitionVO>> flows(@PathVariable String systemId,
                                                           @RequestParam(defaultValue = "1") int pageNo,
                                                           @RequestParam(defaultValue = "20") int pageSize,
                                                           FlowQueryRequest query) {
        return ApiResponse.success(flowDefinitionService.flows(systemId,
                new PageRequest(pageNo, pageSize, null, List.of(), List.of()), query));
    }

    @PostMapping("/api/v1/systems/{systemId}/flows")
    public ApiResponse<FlowDefinitionVO> createFlow(@PathVariable String systemId,
                                                    @RequestBody FlowSaveRequest request) {
        return ApiResponse.success(flowDefinitionService.saveFlow(systemId, null, request));
    }

    @GetMapping("/api/v1/systems/{systemId}/flows/{flowId}")
    public ApiResponse<FlowDefinitionVO> flowDetail(@PathVariable String systemId, @PathVariable String flowId) {
        return ApiResponse.success(flowDefinitionService.flowDetail(systemId, flowId));
    }

    @PatchMapping("/api/v1/systems/{systemId}/flows/{flowId}")
    public ApiResponse<FlowDefinitionVO> updateFlow(@PathVariable String systemId, @PathVariable String flowId,
                                                    @RequestBody FlowSaveRequest request) {
        return ApiResponse.success(flowDefinitionService.saveFlow(systemId, flowId, request));
    }

    @GetMapping("/api/v1/systems/{systemId}/flows/node-library")
    public ApiResponse<List<NodeLibraryItem>> nodeLibrary(@PathVariable String systemId) {
        return ApiResponse.success(flowDefinitionService.nodeLibrary(systemId));
    }

    @GetMapping("/api/v1/systems/{systemId}/flows/{flowId}/canvas")
    public ApiResponse<CanvasVO> canvas(@PathVariable String systemId, @PathVariable String flowId) {
        return ApiResponse.success(flowDefinitionService.canvas(systemId, flowId));
    }

    @PutMapping("/api/v1/systems/{systemId}/flows/{flowId}/canvas")
    public ApiResponse<FlowDefinitionVO> saveCanvas(@PathVariable String systemId, @PathVariable String flowId,
                                                    @RequestBody CanvasSaveRequest request) {
        return ApiResponse.success(flowDefinitionService.saveCanvas(systemId, flowId, request));
    }

    @GetMapping("/api/v1/systems/{systemId}/flows/{flowId}/nodes/{nodeKey}/properties")
    public ApiResponse<NodePropertyPanelVO> nodeProperties(@PathVariable String systemId,
                                                           @PathVariable String flowId,
                                                           @PathVariable String nodeKey) {
        return ApiResponse.success(flowDefinitionService.nodeProperties(systemId, flowId, nodeKey));
    }

    @PostMapping("/api/v1/systems/{systemId}/flows/{flowId}/simulate")
    public ApiResponse<FlowSimulationResult> simulate(@PathVariable String systemId, @PathVariable String flowId,
                                                      @RequestBody(required = false) FlowSimulationRequest request) {
        return ApiResponse.success(flowDefinitionService.simulate(systemId, flowId, request));
    }

    @PostMapping("/api/v1/systems/{systemId}/flows/{flowId}/publish-check")
    public ApiResponse<PublishCheckResultVO> publishCheck(@PathVariable String systemId, @PathVariable String flowId,
                                                          @RequestBody(required = false) PublishRequest request) {
        return ApiResponse.success(flowDefinitionService.publishCheck(systemId, flowId, request));
    }

    @PostMapping("/api/v1/systems/{systemId}/flows/{flowId}/publish")
    public ApiResponse<PublishResult> publish(@PathVariable String systemId, @PathVariable String flowId,
                                              @RequestBody(required = false) PublishRequest request) {
        return ApiResponse.success(flowDefinitionService.publish(systemId, flowId, request));
    }

    @GetMapping("/api/v1/systems/{systemId}/flows/{flowId}/snapshots")
    public ApiResponse<List<FlowSnapshotVO>> snapshots(@PathVariable String systemId, @PathVariable String flowId) {
        return ApiResponse.success(flowDefinitionService.snapshots(systemId, flowId));
    }

    @GetMapping("/api/v1/systems/{systemId}/flows/{flowId}/snapshots/{versionNo}")
    public ApiResponse<FlowSnapshotVO> snapshotDetail(@PathVariable String systemId, @PathVariable String flowId,
                                                      @PathVariable String versionNo) {
        return ApiResponse.success(flowDefinitionService.snapshotDetail(systemId, flowId, versionNo));
    }

    @GetMapping("/api/v1/systems/{systemId}/flows/{flowId}/impact-analysis")
    public ApiResponse<ImpactAnalysisVO> impactAnalysis(@PathVariable String systemId, @PathVariable String flowId,
                                                        @RequestParam(required = false) String versionNo) {
        return ApiResponse.success(flowDefinitionService.impactAnalysis(systemId, flowId, versionNo));
    }
}
