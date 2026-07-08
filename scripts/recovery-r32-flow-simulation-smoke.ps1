param(
    [string]$BaseUrl = 'http://127.0.0.1:18131',
    [switch]$KeepCreatedData
)

$ErrorActionPreference = 'Stop'

function Invoke-Api {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [object]$Body = $null,
        [hashtable]$Headers = @{}
    )

    $uri = "$BaseUrl$Path"
    $jsonBody = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 80 -Compress }
    try {
        if ($null -eq $jsonBody) {
            $response = Invoke-RestMethod -Method $Method -Uri $uri -Headers $Headers -TimeoutSec 30
        } else {
            $response = Invoke-RestMethod -Method $Method -Uri $uri -Headers $Headers `
                -ContentType 'application/json; charset=utf-8' -Body $jsonBody -TimeoutSec 30
        }
    } catch {
        $status = 0
        $body = $_.Exception.Message
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
            try {
                $reader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
                $body = $reader.ReadToEnd()
            } catch {
                $body = $_.Exception.Message
            }
        }
        throw "API failed: $Method $Path -> HTTP $status $body"
    }
    if ($response.code -ne 'SUCCESS') {
        throw "API failed: $Method $Path -> $($response | ConvertTo-Json -Depth 30 -Compress)"
    }
    return $response.data
}

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) {
        throw $Message
    }
}

function Remove-CreatedSystems {
    if ($KeepCreatedData -or $null -eq $script:AdminHeaders) {
        return @('SKIPPED')
    }
    $results = @()
    foreach ($systemId in @($script:CreatedSystemIds)) {
        if ([string]::IsNullOrWhiteSpace($systemId)) {
            continue
        }
        try {
            $deleted = Invoke-Api -Method 'Delete' -Path "/api/v1/platform/systems/$systemId" -Headers $script:AdminHeaders -Body @{
                reason = 'recovery-R32 flow simulation cleanup'
                impactScope = 'created_by_current_script'
                idempotencyKey = "cleanup-R32-$script:Suffix-$systemId"
            }
            $results += "${systemId}:$($deleted.result)"
        } catch {
            $results += "${systemId}:FAILED:$($_.Exception.Message)"
        }
    }
    return $results
}

trap {
    $originalError = $_
    if (-not $KeepCreatedData) {
        try {
            $script:CleanupResult = Remove-CreatedSystems
        } catch {
            Write-Error "Cleanup created systems failed: $($_.Exception.Message)"
        }
    }
    throw $originalError
}

$script:Suffix = "$(Get-Date -Format 'MMddHHmmss')_$((New-Guid).ToString('N').Substring(0, 6))"
$script:CreatedSystemIds = New-Object System.Collections.Generic.List[string]
$script:AdminHeaders = $null
$script:CleanupResult = @('SKIPPED')
$EvidenceDir = Join-Path (Get-Location) 'docs\evidence\recovery\screenshots\r32-flow-simulation'
New-Item -ItemType Directory -Force -Path $EvidenceDir | Out-Null

$Health = Invoke-Api -Method 'Get' -Path '/api/v1/health'
Assert-True -Condition ($Health.status -eq 'UP' -and $Health.database -eq 'UP' -and $Health.schema -eq 'UP' -and $Health.redis -eq 'UP') `
    -Message "Health is not fully UP: $($Health | ConvertTo-Json -Depth 12 -Compress)"

$AdminLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = 'admin'
    password = '123123aa'
    loginTarget = 'PLATFORM'
}
$script:AdminHeaders = @{ Authorization = "Bearer $($AdminLogin.accessToken)" }

$System = Invoke-Api -Method 'Post' -Path '/api/v1/platform/systems' -Headers $script:AdminHeaders -Body @{
    systemName = "R32 Flow Simulation $script:Suffix"
    systemCode = "r32_flow_sim_$script:Suffix"
    tenantMode = 1
    templateCode = 'blank'
}
$SystemId = [string]$System.systemId
$script:CreatedSystemIds.Add($SystemId) | Out-Null

$Flow = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/flows" -Headers $script:AdminHeaders -Body @{
    flowCode = "flow_r32_$script:Suffix"
    flowName = "R32 Flow Simulation $script:Suffix"
    boundModuleId = $null
    triggerRule = @{
        triggerType = 'MANUAL_ACTION'
        actionCodes = @('record.submitApproval')
        conditionExpression = 'amount branch simulation'
        manualStartAllowed = $true
        idempotencyRequired = $true
    }
    status = 1
    canvas = @{
        nodes = @()
        edges = @()
    }
    description = 'R32 flow simulation smoke'
}
$FlowId = [string]$Flow.flowId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($FlowId)) -Message 'Flow create did not return flowId.'

$Canvas = @{
    nodes = @(
        @{
            nodeKey = 'node_submit_review'
            nodeType = 'approval'
            nodeName = 'Submit Review'
            position = @{ x = 120; y = 120; width = 180; height = 72 }
            propertyPayload = @{
                approvalType = 'OR_SIGN'
                assigneeType = 'ROLE'
                assigneeIds = @('role_flow_approver')
                allowTransfer = $true
                allowReject = $true
                reasonRequired = $false
            }
            status = 1
        },
        @{
            nodeKey = 'node_amount_condition'
            nodeType = 'condition'
            nodeName = 'Amount Branch'
            position = @{ x = 380; y = 120; width = 180; height = 72 }
            propertyPayload = @{
                ruleMode = 'FIRST_MATCH'
                defaultBranchLabel = 'default'
                unmatchedPolicy = 'FOLLOW_DEFAULT_BRANCH'
            }
            status = 1
        },
        @{
            nodeKey = 'node_manager_review'
            nodeType = 'approval'
            nodeName = 'Manager Review'
            position = @{ x = 640; y = 60; width = 180; height = 72 }
            propertyPayload = @{
                approvalType = 'OR_SIGN'
                assigneeType = 'ROLE'
                assigneeIds = @('role_manager_reviewer')
                allowTransfer = $true
                allowReject = $true
                reasonRequired = $true
            }
            status = 1
        },
        @{
            nodeKey = 'node_end_passed'
            nodeType = 'end'
            nodeName = 'End Passed'
            position = @{ x = 900; y = 120; width = 180; height = 72 }
            propertyPayload = @{}
            status = 1
        }
    )
    edges = @(
        @{
            edgeKey = 'edge_submit_condition'
            sourceNodeKey = 'node_submit_review'
            targetNodeKey = 'node_amount_condition'
            branchLabel = 'approved'
            conditionPayload = $null
        },
        @{
            edgeKey = 'edge_condition_manager'
            sourceNodeKey = 'node_amount_condition'
            targetNodeKey = 'node_manager_review'
            branchLabel = 'amount >= 100000'
            conditionPayload = @{
                expressionId = 'expr_amount_gte_100000'
                fieldCode = 'amount'
                operator = 'GTE'
                expectedValue = 100000
                expressionText = 'amount GTE 100000'
            }
        },
        @{
            edgeKey = 'edge_condition_end'
            sourceNodeKey = 'node_amount_condition'
            targetNodeKey = 'node_end_passed'
            branchLabel = 'amount < 100000'
            conditionPayload = @{
                expressionId = 'expr_amount_lt_100000'
                fieldCode = 'amount'
                operator = 'LT'
                expectedValue = 100000
                expressionText = 'amount LT 100000'
            }
        },
        @{
            edgeKey = 'edge_manager_end'
            sourceNodeKey = 'node_manager_review'
            targetNodeKey = 'node_end_passed'
            branchLabel = 'manager approved'
            conditionPayload = $null
        }
    )
}

$Saved = Invoke-Api -Method 'Put' -Path "/api/v1/systems/$SystemId/flows/$FlowId/canvas" -Headers $script:AdminHeaders -Body $Canvas
Assert-True -Condition (@($Saved.canvas.nodes).Count -eq 4 -and @($Saved.canvas.edges).Count -eq 4) -Message 'Canvas save did not read back expected nodes/edges.'

$HighSimulation = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/flows/$FlowId/simulate" -Headers $script:AdminHeaders -Body @{
    versionNo = 'DRAFT'
    recordId = "r32_high_$script:Suffix"
    actorMemberId = 'member_requester'
    fieldValues = @{
        amount = 120000
    }
    idempotencyKey = "r32-sim-high-$script:Suffix"
}
$LowSimulation = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/flows/$FlowId/simulate" -Headers $script:AdminHeaders -Body @{
    versionNo = 'DRAFT'
    recordId = "r32_low_$script:Suffix"
    actorMemberId = 'member_requester'
    fieldValues = @{
        amount = 80000
    }
    idempotencyKey = "r32-sim-low-$script:Suffix"
}

$HighNodes = @($HighSimulation.stepTraces | ForEach-Object { $_.nodeKey })
$LowNodes = @($LowSimulation.stepTraces | ForEach-Object { $_.nodeKey })
$HighDecision = @($HighSimulation.conditionDecisions | Where-Object { $_.selectedEdgeKey -eq 'edge_condition_manager' })
$LowDecision = @($LowSimulation.conditionDecisions | Where-Object { $_.selectedEdgeKey -eq 'edge_condition_end' })

Assert-True -Condition ($HighSimulation.passed -eq $true) -Message "High amount simulation failed: $($HighSimulation | ConvertTo-Json -Depth 40 -Compress)"
Assert-True -Condition ($LowSimulation.passed -eq $true) -Message "Low amount simulation failed: $($LowSimulation | ConvertTo-Json -Depth 40 -Compress)"
Assert-True -Condition ($HighNodes -contains 'node_manager_review') -Message 'High amount path did not include manager review.'
Assert-True -Condition (-not ($LowNodes -contains 'node_manager_review')) -Message 'Low amount path unexpectedly included manager review.'
Assert-True -Condition (@($HighDecision).Count -ge 1 -and @($LowDecision).Count -ge 1) -Message 'Condition decisions did not select different branches.'
Assert-True -Condition (@($HighSimulation.predictedApprovers).Count -eq 2) -Message 'High amount path should predict two approval nodes.'
Assert-True -Condition (@($LowSimulation.predictedApprovers).Count -eq 1) -Message 'Low amount path should predict one approval node.'
Assert-True -Condition ($HighSimulation.runtimeInstanceCreated -eq $false -and $LowSimulation.runtimeInstanceCreated -eq $false) -Message 'Simulation created a runtime instance.'
Assert-True -Condition (@($HighSimulation.impactRefs).Count -ge 1 -and @($LowSimulation.impactRefs).Count -ge 1) -Message 'Simulation did not return impact refs.'
Assert-True -Condition (@($HighSimulation.failureItems).Count -eq 0 -and @($LowSimulation.failureItems).Count -eq 0) -Message 'Simulation returned unexpected blockers.'

$BlockerFlow = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/flows" -Headers $script:AdminHeaders -Body @{
    flowCode = "flow_r32_blocker_$script:Suffix"
    flowName = "R32 Flow Simulation Blocker $script:Suffix"
    boundModuleId = $null
    triggerRule = @{
        triggerType = 'MANUAL_ACTION'
        actionCodes = @('record.submitApproval')
        conditionExpression = ''
        manualStartAllowed = $true
        idempotencyRequired = $true
    }
    status = 1
    canvas = @{
        nodes = @(
            @{
                nodeKey = 'node_review_without_assignee'
                nodeType = 'approval'
                nodeName = 'Review Without Assignee'
                position = @{ x = 120; y = 120; width = 180; height = 72 }
                propertyPayload = @{
                    approvalType = 'OR_SIGN'
                    assigneeType = 'ROLE'
                    assigneeIds = @()
                }
                status = 1
            },
            @{
                nodeKey = 'node_end'
                nodeType = 'end'
                nodeName = 'End'
                position = @{ x = 380; y = 120; width = 180; height = 72 }
                propertyPayload = @{}
                status = 1
            }
        )
        edges = @(
            @{
                edgeKey = 'edge_review_end'
                sourceNodeKey = 'node_review_without_assignee'
                targetNodeKey = 'node_end'
                branchLabel = 'approved'
                conditionPayload = $null
            }
        )
    }
    description = 'R32 blocker evidence'
}
$BlockerSimulation = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/flows/$($BlockerFlow.flowId)/simulate" -Headers $script:AdminHeaders -Body @{
    versionNo = 'DRAFT'
    fieldValues = @{
        amount = 1
    }
    idempotencyKey = "r32-sim-blocker-$script:Suffix"
}
Assert-True -Condition ($BlockerSimulation.passed -eq $false) -Message 'Blocker simulation unexpectedly passed.'
Assert-True -Condition (@($BlockerSimulation.blockerItems | Where-Object { $_.itemCode -eq 'E_SIM_APPROVER_EMPTY' }).Count -ge 1) -Message 'Blocker simulation did not report missing approver.'
Assert-True -Condition ($BlockerSimulation.runtimeInstanceCreated -eq $false) -Message 'Blocker simulation created a runtime instance.'

$ApiAuditPath = Join-Path $EvidenceDir 'flow-simulation-api-audit.json'
$ApiAudit = [ordered]@{
    systemId = $SystemId
    flowId = $FlowId
    highTraceId = $HighSimulation.traceId
    lowTraceId = $LowSimulation.traceId
    highNodes = $HighNodes
    lowNodes = $LowNodes
    highApproverCount = @($HighSimulation.predictedApprovers).Count
    lowApproverCount = @($LowSimulation.predictedApprovers).Count
    highRuntimeInstanceCreated = $HighSimulation.runtimeInstanceCreated
    lowRuntimeInstanceCreated = $LowSimulation.runtimeInstanceCreated
    blockerItemCodes = @($BlockerSimulation.blockerItems | ForEach-Object { $_.itemCode })
}
$ApiAudit | ConvertTo-Json -Depth 40 | Set-Content -Encoding UTF8 -Path $ApiAuditPath

$script:CleanupResult = Remove-CreatedSystems

$Result = [ordered]@{
    status = 'PASS'
    task = 'REC-P0-032'
    baseUrl = $BaseUrl
    systemId = $SystemId
    flowId = $FlowId
    highPath = $HighNodes
    lowPath = $LowNodes
    highApproverCount = @($HighSimulation.predictedApprovers).Count
    lowApproverCount = @($LowSimulation.predictedApprovers).Count
    highRuntimeInstanceCreated = $HighSimulation.runtimeInstanceCreated
    lowRuntimeInstanceCreated = $LowSimulation.runtimeInstanceCreated
    blockerPassed = $BlockerSimulation.passed
    blockerItemCodes = @($BlockerSimulation.blockerItems | ForEach-Object { $_.itemCode })
    cleanup = $script:CleanupResult
    apiAuditPath = $ApiAuditPath
}

$resultPath = Join-Path (Get-Location) 'docs\evidence\recovery\r32-flow-simulation-result.json'
$Result | ConvertTo-Json -Depth 40 | Set-Content -Encoding UTF8 -Path $resultPath

$summaryPath = Join-Path (Get-Location) 'docs\evidence\recovery\r32-flow-simulation-2026-06-30.md'
$summary = @(
    '# R32 Flow Simulation First Loop Evidence',
    '',
    "- Base URL: $BaseUrl",
    "- System: $SystemId",
    "- Flow: $FlowId",
    "- High amount path: $($HighNodes -join ' -> ')",
    "- Low amount path: $($LowNodes -join ' -> ')",
    "- Predicted approvers: high=$(@($HighSimulation.predictedApprovers).Count), low=$(@($LowSimulation.predictedApprovers).Count)",
    "- Runtime instance created by simulation: high=$($HighSimulation.runtimeInstanceCreated), low=$($LowSimulation.runtimeInstanceCreated)",
    "- Missing approver blocker codes: $((@($BlockerSimulation.blockerItems | ForEach-Object { $_.itemCode })) -join ', ')",
    "- Cleanup: $($script:CleanupResult -join ', ')",
    '',
    'Evidence files:',
    "- $resultPath",
    "- $ApiAuditPath"
)
$summary | Set-Content -Encoding UTF8 -Path $summaryPath

$Result | ConvertTo-Json -Depth 40
