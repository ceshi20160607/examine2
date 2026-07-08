param(
    [string]$BaseUrl = 'http://127.0.0.1:18131',
    [switch]$KeepCreatedData,
    [switch]$NoFailExit
)

$ErrorActionPreference = 'Stop'

function Invoke-Api {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [object]$Body = $null,
        [hashtable]$Headers = @{}
    )
    $jsonBody = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 100 -Compress }
    try {
        if ($null -eq $jsonBody) {
            $response = Invoke-RestMethod -Method $Method -Uri "$BaseUrl$Path" -Headers $Headers -TimeoutSec 60
        } else {
            $response = Invoke-RestMethod -Method $Method -Uri "$BaseUrl$Path" -Headers $Headers `
                -ContentType 'application/json; charset=utf-8' -Body $jsonBody -TimeoutSec 60
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

function Invoke-ExpectedHttpStatus {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][int[]]$ExpectedStatus,
        [object]$Body = $null,
        [hashtable]$Headers = @{}
    )
    $jsonBody = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 100 -Compress }
    try {
        if ($null -eq $jsonBody) {
            $null = Invoke-RestMethod -Method $Method -Uri "$BaseUrl$Path" -Headers $Headers -TimeoutSec 60
        } else {
            $null = Invoke-RestMethod -Method $Method -Uri "$BaseUrl$Path" -Headers $Headers `
                -ContentType 'application/json; charset=utf-8' -Body $jsonBody -TimeoutSec 60
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
        Assert-True -Condition ($ExpectedStatus -contains $status) -Message "Expected HTTP $($ExpectedStatus -join '/') for $Method $Path but got HTTP $status. Body=$body"
        return @{ status = $status; path = $Path; body = $body }
    }
    throw "Expected HTTP $($ExpectedStatus -join '/') but request succeeded: $Method $Path"
}

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) {
        throw $Message
    }
}

function Get-FreeTcpPort {
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
    $listener.Start()
    $port = $listener.LocalEndpoint.Port
    $listener.Stop()
    return $port
}

function Find-Chrome {
    $paths = @(
        "$env:ProgramFiles\Google\Chrome\Application\chrome.exe",
        "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe",
        "$env:LocalAppData\Google\Chrome\Application\chrome.exe",
        "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe",
        "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe"
    )
    foreach ($path in $paths) {
        if ($path -and (Test-Path -LiteralPath $path)) {
            return $path
        }
    }
    throw 'Chrome or Edge executable was not found.'
}

function New-FieldBody {
    param([string]$Code, [string]$Name, [string]$Type, [bool]$Required = $false)
    return @{
        fieldCode = $Code
        name = $Name
        fieldType = $Type
        storageType = if ($Type -eq 'NUMBER') { 'DECIMAL' } else { 'VARCHAR' }
        required = $Required
        sortable = $true
        dictTypeId = $null
        permissionMetadata = $null
        maskRule = 'NONE'
        importExportRule = @{
            importable = $true
            exportable = $true
            requiredOnImport = $Required
            duplicateKey = 'none'
            desensitizeMode = 'none'
        }
    }
}

function Remove-CreatedSystems {
    if ($KeepCreatedData -or $null -eq $script:AdminHeaders) {
        return @('SKIPPED')
    }
    $results = @()
    foreach ($systemId in @($script:SystemId, $script:NormalOwnedSystemId)) {
        if ([string]::IsNullOrWhiteSpace([string]$systemId)) {
            continue
        }
        try {
            $deleted = Invoke-Api -Method 'Delete' -Path "/api/v1/platform/systems/$systemId" -Headers $script:AdminHeaders -Body @{
                reason = 'recovery-r49 openapi assistant integration cleanup'
                impactScope = 'created_by_current_script'
                idempotencyKey = "cleanup-r49-$script:Suffix-$systemId"
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
    if ($script:ChromeProcess -and -not $script:ChromeProcess.HasExited) {
        Stop-Process -Id $script:ChromeProcess.Id -Force -ErrorAction SilentlyContinue
    }
    if ($script:ChromeProfileDir -and (Test-Path -LiteralPath $script:ChromeProfileDir)) {
        Remove-Item -LiteralPath $script:ChromeProfileDir -Recurse -Force -ErrorAction SilentlyContinue
    }
    if (-not $KeepCreatedData) {
        try {
            $script:CleanupResult = Remove-CreatedSystems
        } catch {
            Write-Error "Cleanup created systems failed: $($_.Exception.Message)"
        }
    }
    throw $originalError
}

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$script:Suffix = "$(Get-Date -Format 'MMddHHmmssfff')_$((New-Guid).ToString('N').Substring(0, 6))"
$Password = 'Aa123456!'
$script:SystemId = $null
$script:NormalOwnedSystemId = $null
$script:AdminHeaders = $null
$script:ChromeProcess = $null
$script:ChromeProfileDir = $null
$script:CleanupResult = @('SKIPPED')
$EvidenceDir = Join-Path $RepoRoot 'docs\evidence\recovery\screenshots\r49-openapi-assistant-integration'
$ResultFile = Join-Path $RepoRoot 'docs\evidence\recovery\r49-openapi-assistant-integration-result.json'
$SummaryFile = Join-Path $RepoRoot 'docs\evidence\recovery\r49-openapi-assistant-integration-2026-07-01.md'
New-Item -ItemType Directory -Force -Path $EvidenceDir | Out-Null
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $ResultFile) | Out-Null

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
    systemName = "R49 Integration $script:Suffix"
    systemCode = "r49_integration_$script:Suffix"
    tenantMode = 1
    templateCode = 'default'
}
$script:SystemId = [string]$System.systemId
$Options = Invoke-Api -Method 'Get' -Path '/api/v1/platform/system-switch/options' -Headers $script:AdminHeaders
$SystemOption = @($Options | Where-Object { [string]$_.systemId -eq $script:SystemId }) | Select-Object -First 1
$TenantId = [string]$SystemOption.tenantId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($TenantId)) -Message 'Created system tenant id missing.'

$null = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $script:AdminHeaders -Body @{
    systemId = $script:SystemId
    tenantId = $TenantId
    reason = 'recovery-r49 admin setup'
}

$Role = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/roles" -Headers $script:AdminHeaders -Body @{
    roleName = "R49 Runtime Member $script:Suffix"
    roleCode = "R49_RUNTIME_MEMBER_$script:Suffix"
    roleType = 'CUSTOM'
    status = 1
    description = 'Recovery R49 runtime role'
}
$Group = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/module-groups" -Headers $script:AdminHeaders -Body @{
    name = "R49 Business Group $script:Suffix"
    sort = 10
    visibleRoleIds = @([string]$Role.roleId)
    publishStatus = 'DRAFT'
}
$Module = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/modules" -Headers $script:AdminHeaders -Body @{
    groupId = $Group.groupId
    moduleCode = "r49_contract_$script:Suffix"
    name = "R49 Contract Ledger $script:Suffix"
    status = 1
    description = 'Recovery R49 OpenAPI and Agent module'
}
$ModuleId = [string]$Module.moduleId
$Fields = @()
$Fields += Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code 'contractName' -Name 'Contract Name' -Type 'TEXT' -Required $true)
$Fields += Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code 'contractAmount' -Name 'Contract Amount' -Type 'NUMBER')
$Fields += Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code 'sourceTag' -Name 'Source Tag' -Type 'TEXT')
$FieldIds = @($Fields | ForEach-Object { [string]$_.fieldId })
$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/modules/$ModuleId/scenes" -Headers $script:AdminHeaders -Body @{
    sceneCode = 'default'
    sceneName = 'All Contracts'
    defaultScene = $true
    visibleRoleIds = @([string]$Role.roleId)
    columnFieldIds = $FieldIds
    filterFieldIds = @([string]$Fields[0].fieldId)
    sortFieldIds = @([string]$Fields[1].fieldId)
}
$null = Invoke-Api -Method 'Put' -Path "/api/v1/systems/$script:SystemId/roles/$($Role.roleId)/permissions" -Headers $script:AdminHeaders -Body @{
    menuPermissions = @{}
    modulePermissions = @{ '*' = $true }
    actionPermissions = @{ '*' = $true; 'record.create' = $true; 'record.edit' = $true; 'record.delete' = $true }
    fieldPermissions = @{ '*' = 'WRITABLE' }
    dataScopeRules = @(@{ type = 'ALL' })
    denyPolicies = @()
}
$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/modules/$ModuleId/publish" -Headers $script:AdminHeaders -Body @{
    reason = 'recovery-r49 module publish'
    idempotencyKey = "module-publish-r49-$script:Suffix"
}

$SecretMaterial = "material-r49-$script:Suffix"
$OpenApiApp = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/openapi/apps" -Headers ($script:AdminHeaders + @{ 'Idempotency-Key' = "openapi-create-r49-$script:Suffix" }) -Body @{
    externalAppCode = "r49_app_$script:Suffix"
    appName = "R49 OpenAPI App $script:Suffix"
    status = 1
    scopes = @('record.data.read', 'record.data.write')
    callbackUrl = "https://callback.example/r49/$script:Suffix"
    rateLimit = @{ dimension = 'APP'; windowSeconds = 60; limit = 120; burstLimit = 30; overflowPolicy = 'REJECT'; status = 1 }
    secretMaterialRef = $SecretMaterial
    requestedSecretVersion = "v1-$script:Suffix"
    idempotencyKey = "openapi-create-r49-$script:Suffix"
}
$OpenApiJson = $OpenApiApp | ConvertTo-Json -Depth 50 -Compress
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace([string]$OpenApiApp.externalAppId)) -Message 'OpenAPI app create did not return externalAppId.'
Assert-True -Condition ($OpenApiJson -notlike "*$SecretMaterial*" -and $OpenApiJson -notlike '*secretMaterialRef*' -and $OpenApiJson -notlike '*storageRef*') -Message 'OpenAPI create response leaked secret material.'

$RotateMaterial = "material-rotated-r49-$script:Suffix"
$Rotate = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/openapi/apps/$($OpenApiApp.externalAppId)/rotate-secret" -Headers ($script:AdminHeaders + @{ 'Idempotency-Key' = "openapi-rotate-r49-$script:Suffix" }) -Body @{
    newMaterialRef = $RotateMaterial
    requestedVersion = "v2-$script:Suffix"
    dualWriteValidation = $true
    switchAfterValidation = $true
    rollbackPlan = 'keep previous SecretRef until validation passes'
    idempotencyKey = "openapi-rotate-r49-$script:Suffix"
}
$RotateJson = $Rotate | ConvertTo-Json -Depth 50 -Compress
Assert-True -Condition ($Rotate.task.bizType -eq 'OPENAPI_SECRET_ROTATION' -and $RotateJson -notlike "*$RotateMaterial*" -and $RotateJson -notlike '*storageRef*') -Message 'OpenAPI rotate did not create a masked rotation task.'
$SecretRefId = [string]$Rotate.openApiSecretRef.secretRefId
if ([string]::IsNullOrWhiteSpace($SecretRefId)) { $SecretRefId = [string]$OpenApiApp.openApiSecretRef.secretRefId }
$OpenApiHeaders = @{
    'X-OpenAPI-App-Code' = [string]$OpenApiApp.externalAppCode
    'X-OpenAPI-Secret-Ref' = $SecretRefId
    'Idempotency-Key' = "openapi-record-create-r49-$script:Suffix"
}
$ExternalCreate = Invoke-Api -Method 'Post' -Path "/openapi/v1/systems/$script:SystemId/modules/$ModuleId/records" -Headers $OpenApiHeaders -Body @{
    fieldValues = @{ contractName = "OpenAPI Contract $script:Suffix"; contractAmount = 4900; sourceTag = 'openapi-r49' }
    sourceType = 'OPENAPI_R49'
}
$OpenApiRecordId = [string]$ExternalCreate.recordId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($OpenApiRecordId)) -Message 'OpenAPI external create did not return recordId.'
$ExternalSearch = Invoke-Api -Method 'Post' -Path "/openapi/v1/systems/$script:SystemId/modules/$ModuleId/records/search" -Headers $OpenApiHeaders -Body @{
    pageNo = 1
    pageSize = 20
    keyword = $script:Suffix
    fields = @('contractName', 'contractAmount', 'sourceTag')
}
Assert-True -Condition ([int]$ExternalSearch.total -ge 1 -or [int]$ExternalSearch.page.total -ge 1) -Message 'OpenAPI external search did not return created record.'
$ExternalDetail = Invoke-Api -Method 'Get' -Path "/openapi/v1/systems/$script:SystemId/modules/$ModuleId/records/$OpenApiRecordId" -Headers $OpenApiHeaders
Assert-True -Condition ([string]$ExternalDetail.recordId -eq $OpenApiRecordId) -Message 'OpenAPI external detail did not return created record.'

$ReadOnlyApp = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/openapi/apps" -Headers ($script:AdminHeaders + @{ 'Idempotency-Key' = "openapi-readonly-r49-$script:Suffix" }) -Body @{
    externalAppCode = "r49_readonly_$script:Suffix"
    appName = "R49 Readonly OpenAPI App $script:Suffix"
    status = 1
    scopes = @('record.data.read')
    secretMaterialRef = "readonly-material-r49-$script:Suffix"
    requestedSecretVersion = "v1-readonly-$script:Suffix"
    idempotencyKey = "openapi-readonly-r49-$script:Suffix"
}
$ReadOnlyDenied = Invoke-ExpectedHttpStatus -Method 'Post' -Path "/openapi/v1/systems/$script:SystemId/modules/$ModuleId/records" -ExpectedStatus @(403) -Headers @{
    'X-OpenAPI-App-Code' = [string]$ReadOnlyApp.externalAppCode
    'X-OpenAPI-Secret-Ref' = [string]$ReadOnlyApp.openApiSecretRef.secretRefId
    'Idempotency-Key' = "openapi-readonly-denied-r49-$script:Suffix"
} -Body @{
    fieldValues = @{ contractName = "Denied OpenAPI Contract $script:Suffix"; contractAmount = 1; sourceTag = 'denied' }
    sourceType = 'OPENAPI_R49_DENIED'
}
$WrongSecretDenied = Invoke-ExpectedHttpStatus -Method 'Post' -Path "/openapi/v1/systems/$script:SystemId/modules/$ModuleId/records/search" -ExpectedStatus @(401, 403) -Headers @{
    'X-OpenAPI-App-Code' = [string]$OpenApiApp.externalAppCode
    'X-OpenAPI-Secret-Ref' = "wrong-secret-r49-$script:Suffix"
} -Body @{ pageNo = 1; pageSize = 1; keyword = $script:Suffix; fields = @('contractName') }

$CallLogs = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/openapi/call-logs/search?pageNo=1&pageSize=80" -Headers $script:AdminHeaders -Body @{
    externalAppId = [string]$OpenApiApp.externalAppId
    externalAppCode = [string]$OpenApiApp.externalAppCode
}
$OpenApiSuccessLogs = @($CallLogs.records | Where-Object { [string]$_.result -eq 'SUCCESS' })
$OpenApiFailedLogs = @($CallLogs.records | Where-Object { [string]$_.result -eq 'FAILED' })
Assert-True -Condition ($OpenApiSuccessLogs.Count -ge 4) -Message 'OpenAPI call logs did not capture enough success entries.'
Assert-True -Condition ($OpenApiFailedLogs.Count -ge 1) -Message 'OpenAPI call logs did not capture failed authentication or permission entries.'

$ModelAuth = Invoke-Api -Method 'Post' -Path '/api/v1/platform/agent/model-authorizations' -Headers ($script:AdminHeaders + @{ 'Idempotency-Key' = "r49-model-auth-$script:Suffix" }) -Body @{
    authorizationCode = "r49_model_$script:Suffix"
    modelProvider = 'LOCAL'
    modelName = "R49 Local Model $script:Suffix"
    modelCredentialRef = @{ secretRefId = "sec_model_r49_$script:Suffix"; refType = 'MODEL'; version = "v1-$script:Suffix"; displayName = 'R49 model credential' }
    quota = @{ tokenLimit = 100000; requestLimit = 10000; costLimit = 0; resetPolicy = 'MONTHLY' }
    dataOutboundPolicy = @{ allowExternalModel = $false; allowedRegions = @(); outboundFields = @(); retentionDays = 0 }
    status = 1
    idempotencyKey = "r49-model-auth-$script:Suffix"
}
$ModelAuthJson = $ModelAuth | ConvertTo-Json -Depth 50 -Compress
Assert-True -Condition ($ModelAuthJson -notlike '*storageRef*' -and $ModelAuthJson -notlike '*secretMaterial*') -Message 'Model authorization response exposed secret material.'

$PlatformSession = Invoke-Api -Method 'Post' -Path '/api/v1/platform/agent/sessions' -Headers $script:AdminHeaders -Body @{
    authorizationCode = [string]$ModelAuth.authorizationCode
    promptVersion = "r49_platform_prompt_$script:Suffix"
    openingQuestion = 'Check platform scope and do not touch system business data.'
    metadata = @{ source = 'r49' }
}
$PlatformDenied = Invoke-Api -Method 'Post' -Path '/api/v1/platform/agent/platform-agent-confirm' -Headers ($script:AdminHeaders + @{ 'Idempotency-Key' = "r49-platform-deny-$script:Suffix" }) -Body @{
    sessionId = [string]$PlatformSession.sessionId
    sourceConversation = 'Attempt forbidden system write from platform Agent'
    platformActions = @(@{ actionType = 'SYSTEM_WRITE'; title = 'Forbidden system write'; payload = @{ systemId = $script:SystemId } })
    targetScope = 'system'
    humanConfirmed = $true
    idempotencyKey = "r49-platform-deny-$script:Suffix"
}
Assert-True -Condition ($PlatformDenied.allowed -eq $false -and $PlatformDenied.confirmation.status -eq 'REJECTED_BY_SCOPE') -Message 'Platform Agent did not reject system business write.'

$PolicyCode = "r49_policy_$script:Suffix"
$AgentPolicy = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/agent/policies" -Headers ($script:AdminHeaders + @{ 'Idempotency-Key' = "r49-policy-$script:Suffix" }) -Body @{
    policyCode = $PolicyCode
    scope = @{
        moduleScope = @('*')
        fieldScope = @{ '*' = @('*') }
        actionScope = @('read', 'statistics', 'requestWriteConfirmation', 'createWorkDraft')
        dataScopeExpression = 'CURRENT_MEMBER_PERMISSION'
        outboundLimit = @{ maxRows = 100; allowFileExport = $false; allowThirdPartyWebhook = $false }
        desensitizePolicy = @{ mode = 'MASK_SENSITIVE'; maskedFields = @('mobile', 'email', 'secretNote'); preview = @{ mobile = '138****0000' } }
        policyVersion = "r49_policy_v_$script:Suffix"
    }
    status = 1
    idempotencyKey = "r49-policy-$script:Suffix"
}
$PolicyCheck = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/agent/policies/$($AgentPolicy.policyId)/publish-check" -Headers $script:AdminHeaders
Assert-True -Condition ($PolicyCheck.passed -eq $true) -Message 'R49 Agent policy publish-check did not pass.'
$AdminSession = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/agent/sessions" -Headers $script:AdminHeaders -Body @{
    authorizationCode = [string]$ModelAuth.authorizationCode
    policyCode = $PolicyCode
    promptVersion = "r49_assistant_prompt_$script:Suffix"
    openingQuestion = 'Generate integration analysis, write preview and daily report draft.'
    metadata = @{ source = 'r49' }
}
$AdminMessage = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/agent/sessions/$($AdminSession.sessionId)/messages" -Headers $script:AdminHeaders -Body @{
    message = 'Generate a write preview and work draft for the OpenAPI-created record.'
    toolHints = @('business_write_preview', 'work_daily_report_draft')
    idempotencyKey = "r49-admin-message-$script:Suffix"
}
$ProposedTypes = @($AdminMessage.proposedConfirmations | ForEach-Object { [string]$_.confirmType })
Assert-True -Condition ($ProposedTypes -contains 'SYSTEM_AGENT_WRITE_CONFIRM' -and $ProposedTypes -contains 'WORK_AGENT_DRAFT_CONFIRM') -Message 'Assistant message did not propose write and work confirmations.'
$WritePreview = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/agent/system-agent-write-confirmations" -Headers ($script:AdminHeaders + @{ 'Idempotency-Key' = "r49-write-preview-$script:Suffix" }) -Body @{
    sessionId = [string]$AdminSession.sessionId
    sourceConversation = 'R49 assistant write preview'
    moduleId = $ModuleId
    recordId = $OpenApiRecordId
    fieldDiffs = @(
        @{ fieldCode = 'sourceTag'; fieldName = 'Source Tag'; beforeValue = 'openapi-r49'; afterValue = 'assistant-reviewed'; writable = $true; disabledReason = $null },
        @{ fieldCode = 'secretNote'; fieldName = 'Secret Note'; beforeValue = 'old'; afterValue = 'new'; writable = $false; disabledReason = 'Field is masked or not writable by current permission snapshot' }
    )
    permissionSnapshotId = [string]$AdminSession.permissionSnapshotId
    approvalRequired = $true
    compensationPlan = 'Rollback Agent write if downstream validation fails.'
    humanConfirmed = $false
    idempotencyKey = "r49-write-preview-$script:Suffix"
}
Assert-True -Condition ($WritePreview.confirmation.status -eq 'WAITING_HUMAN_CONFIRM' -and @($WritePreview.permissionClips).Count -ge 1) -Message 'Assistant write preview did not wait or include permission clipping.'
$ConfirmedWrite = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/agent/system-agent-write-confirmations/$($WritePreview.confirmation.confirmationId)/confirm" -Headers ($script:AdminHeaders + @{ 'Idempotency-Key' = "r49-write-confirm-$script:Suffix" }) -Body @{ reason = 'R49 confirms scoped assistant write preview'; idempotencyKey = "r49-write-confirm-$script:Suffix" }
$RejectPreview = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/agent/system-agent-write-confirmations" -Headers ($script:AdminHeaders + @{ 'Idempotency-Key' = "r49-write-reject-preview-$script:Suffix" }) -Body @{
    sessionId = [string]$AdminSession.sessionId
    sourceConversation = 'R49 rejected assistant write preview'
    moduleId = $ModuleId
    recordId = $OpenApiRecordId
    fieldDiffs = @(@{ fieldCode = 'sourceTag'; fieldName = 'Source Tag'; beforeValue = 'assistant-reviewed'; afterValue = 'unsafe'; writable = $true; disabledReason = $null })
    permissionSnapshotId = [string]$AdminSession.permissionSnapshotId
    approvalRequired = $true
    compensationPlan = 'No-op because rejected.'
    humanConfirmed = $false
    idempotencyKey = "r49-write-reject-preview-$script:Suffix"
}
$RejectedWrite = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/agent/system-agent-write-confirmations/$($RejectPreview.confirmation.confirmationId)/reject" -Headers ($script:AdminHeaders + @{ 'Idempotency-Key' = "r49-write-reject-$script:Suffix" }) -Body @{ reason = 'R49 rejects unsafe assistant write preview'; idempotencyKey = "r49-write-reject-$script:Suffix" }
$DraftPreview = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/work/agent/work-agent-draft-confirm" -Headers ($script:AdminHeaders + @{ 'Idempotency-Key' = "r49-work-draft-preview-$script:Suffix" }) -Body @{
    sessionId = [string]$AdminSession.sessionId
    sourceConversation = 'Draft work report from R49 assistant'
    draftType = 'DAILY_REPORT_DRAFT'
    draftPayload = @{ title = "R49 Daily Report $script:Suffix"; summary = 'Generated from authorized assistant and OpenAPI context.' }
    sourceSnapshot = @(@{ sourceType = 'OPENAPI_RECORD'; sourceId = $OpenApiRecordId; title = 'OpenAPI-created record'; permissionPolicy = 'CURRENT_MEMBER'; included = $true })
    humanConfirmed = $false
    idempotencyKey = "r49-work-draft-preview-$script:Suffix"
}
Assert-True -Condition ($DraftPreview.confirmation.status -eq 'WAITING_HUMAN_CONFIRM' -and $DraftPreview.manualConfirmRequired -eq $true) -Message 'Work draft preview did not wait for human confirmation.'
$DraftConfirmed = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/work/agent/work-agent-draft-confirm" -Headers ($script:AdminHeaders + @{ 'Idempotency-Key' = "r49-work-draft-confirm-$script:Suffix" }) -Body @{
    sessionId = [string]$AdminSession.sessionId
    sourceConversation = 'Confirm R49 work report draft'
    draftType = 'DAILY_REPORT_DRAFT'
    draftPayload = $DraftPreview.draftPayload
    sourceSnapshot = $DraftPreview.sourceSnapshot
    humanConfirmed = $true
    idempotencyKey = "r49-work-draft-confirm-$script:Suffix"
}
Assert-True -Condition ($ConfirmedWrite.confirmation.status -eq 'CONFIRMED' -and $RejectedWrite.confirmation.status -eq 'REJECTED' -and $DraftConfirmed.confirmation.status -eq 'CONFIRMED') -Message 'Assistant confirmation terminal states did not match expected.'

$NormalLoginName = "r49_member_$script:Suffix"
$NormalRegister = Invoke-Api -Method 'Post' -Path '/api/v1/auth/register-with-system' -Body @{
    accountName = $NormalLoginName
    mobile = "19$((Get-Date).ToString('HHmmssfff'))"
    email = "r49_member_$script:Suffix@example.com"
    password = $Password
    systemName = "R49 Owned $script:Suffix"
    systemCode = "r49_owned_$script:Suffix"
    tenantMode = 1
    templateCode = 'blank'
}
$script:NormalOwnedSystemId = [string]$NormalRegister.systemId
$NormalMember = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/members" -Headers $script:AdminHeaders -Body @{
    deptId = $null
    memberName = "R49 Normal Member $script:Suffix"
    employeeNo = "R49NM$((Get-Date).ToString('HHmmssfff'))"
    mobile = "18$((Get-Date).ToString('HHmmssfff'))"
    email = "r49_member_runtime_$script:Suffix@example.com"
    status = 1
    roleIds = @([string]$Role.roleId)
}
$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/members/$($NormalMember.systemMemberId)/bind-account" -Headers $script:AdminHeaders -Body @{ accountId = $null; loginName = $NormalLoginName; bindMode = 'BIND' }
$NormalLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{ loginName = $NormalLoginName; password = $Password; loginTarget = 'PLATFORM' }
$NormalHeaders = @{ Authorization = "Bearer $($NormalLogin.accessToken)" }
$null = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $NormalHeaders -Body @{ systemId = $script:SystemId; tenantId = $TenantId; reason = 'recovery-r49 normal switch' }
$NormalSession = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/agent/sessions" -Headers $NormalHeaders -Body @{
    authorizationCode = [string]$ModelAuth.authorizationCode
    policyCode = $PolicyCode
    promptVersion = "r49_normal_prompt_$script:Suffix"
    openingQuestion = 'Normal member uses assistant in permitted context.'
    metadata = @{ source = 'r49-normal' }
}
$NormalMessage = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/agent/sessions/$($NormalSession.sessionId)/messages" -Headers $NormalHeaders -Body @{
    message = 'Summarize my permitted integration record without admin changes.'
    toolHints = @('statistics_query')
    idempotencyKey = "r49-normal-message-$script:Suffix"
}
Assert-True -Condition ($NormalMessage.audit.scope -eq 'system' -and -not [string]::IsNullOrWhiteSpace([string]$NormalMessage.audit.permissionSnapshotId)) -Message 'Normal assistant message did not return scoped audit.'
$NormalOpenApiDenied = Invoke-ExpectedHttpStatus -Method 'Post' -Path "/api/v1/systems/$script:SystemId/openapi/apps" -Headers $NormalHeaders -ExpectedStatus @(403) -Body @{ externalAppCode = "r49_forbidden_app_$script:Suffix"; appName = 'Forbidden App'; status = 1; scopes = @('record.data.read'); idempotencyKey = "r49-normal-openapi-denied-$script:Suffix" }
$NormalPolicyDenied = Invoke-ExpectedHttpStatus -Method 'Post' -Path "/api/v1/systems/$script:SystemId/agent/policies" -Headers $NormalHeaders -ExpectedStatus @(403) -Body @{ policyCode = "r49_forbidden_policy_$script:Suffix"; scope = @{ moduleScope = @('*'); fieldScope = @{ '*' = @('*') }; actionScope = @('admin'); dataScopeExpression = 'ALL'; outboundLimit = @{ maxRows = 1; allowFileExport = $false; allowThirdPartyWebhook = $false }; desensitizePolicy = @{ mode = 'NONE'; maskedFields = @(); preview = @{} }; policyVersion = "forbidden_$script:Suffix" }; status = 1; idempotencyKey = "r49-normal-policy-denied-$script:Suffix" }

$AgentAuditLogs = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$script:SystemId/agent/audit-logs?pageNo=1&pageSize=100&scope=system" -Headers $script:AdminHeaders
$AgentAuditRecords = @($AgentAuditLogs.records)
$AgentConfirmationAudits = @($AgentAuditRecords | Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_.confirmationId) })
Assert-True -Condition ($AgentAuditRecords.Count -ge 8 -and $AgentConfirmationAudits.Count -ge 4) -Message 'Agent audit logs did not include enough session/message/confirmation records.'

function Start-BrowserAudit {
    $chromePath = Find-Chrome
    $debugPort = Get-FreeTcpPort
    $script:ChromeProfileDir = Join-Path $env:TEMP "unexamine-r49-chrome-$script:Suffix"
    New-Item -ItemType Directory -Force -Path $script:ChromeProfileDir | Out-Null
    $script:ChromeProcess = Start-Process -FilePath $chromePath -ArgumentList @(
        '--headless=new',
        '--disable-gpu',
        '--no-first-run',
        '--no-default-browser-check',
        '--disable-background-networking',
        '--remote-allow-origins=*',
        "--remote-debugging-port=$debugPort",
        "--user-data-dir=$script:ChromeProfileDir",
        'about:blank'
    ) -PassThru -WindowStyle Hidden
    $ready = $false
    for ($i = 0; $i -lt 50; $i++) {
        try {
            Invoke-RestMethod -Uri "http://127.0.0.1:$debugPort/json/version" -TimeoutSec 2 | Out-Null
            $ready = $true
            break
        } catch {
            Start-Sleep -Milliseconds 200
        }
    }
    Assert-True -Condition $ready -Message 'Chrome DevTools endpoint did not become ready.'
    return $debugPort
}

function Stop-BrowserAudit {
    if ($script:ChromeProcess -and -not $script:ChromeProcess.HasExited) {
        Stop-Process -Id $script:ChromeProcess.Id -Force -ErrorAction SilentlyContinue
    }
    if ($script:ChromeProfileDir -and (Test-Path -LiteralPath $script:ChromeProfileDir)) {
        Remove-Item -LiteralPath $script:ChromeProfileDir -Recurse -Force -ErrorAction SilentlyContinue
    }
    $script:ChromeProcess = $null
    $script:ChromeProfileDir = $null
}

$debugPort = Start-BrowserAudit
$nodeScript = Join-Path $env:TEMP "unexamine-r49-openapi-assistant-$script:Suffix.js"
$nodeCode = @'
const fs = require('fs');
const path = require('path');
const baseUrl = process.env.R49_BASE_URL;
const port = process.env.R49_CDP_PORT;
const outDir = process.env.R49_EVIDENCE_DIR;
const systemId = process.env.R49_SYSTEM_ID;
const suffix = process.env.R49_SUFFIX;
const roles = {
  admin: { accountId: process.env.R49_ADMIN_ACCOUNT_ID, accessToken: process.env.R49_ADMIN_TOKEN, refreshToken: process.env.R49_ADMIN_REFRESH || '' },
  normal: { accountId: process.env.R49_NORMAL_ACCOUNT_ID, accessToken: process.env.R49_NORMAL_TOKEN, refreshToken: process.env.R49_NORMAL_REFRESH || '' },
};
function delay(ms) { return new Promise((resolve) => setTimeout(resolve, ms)); }
async function cdpJson(pathname, options = {}) {
  const response = await fetch(`http://127.0.0.1:${port}${pathname}`, options);
  if (!response.ok) throw new Error(`CDP HTTP ${response.status} for ${pathname}`);
  return response.json();
}
async function newTarget() {
  try { return await cdpJson(`/json/new?${encodeURIComponent('about:blank')}`, { method: 'PUT' }); }
  catch { return (await cdpJson('/json/list'))[0]; }
}
function connect(wsUrl) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(wsUrl);
    const pending = new Map();
    let seq = 0;
    ws.onopen = () => resolve({
      send(method, params = {}) {
        const id = ++seq;
        ws.send(JSON.stringify({ id, method, params }));
        return new Promise((res, rej) => pending.set(id, { res, rej, method }));
      },
      close() { ws.close(); },
    });
    ws.onerror = reject;
    ws.onmessage = (event) => {
      const message = JSON.parse(event.data);
      if (message.id && pending.has(message.id)) {
        const item = pending.get(message.id);
        pending.delete(message.id);
        if (message.error) item.rej(new Error(`${item.method}: ${JSON.stringify(message.error)}`));
        else item.res(message.result);
      }
    };
  });
}
async function evaluate(client, expression) {
  const result = await client.send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
  if (result.exceptionDetails) throw new Error(JSON.stringify(result.exceptionDetails));
  const value = result.result.value;
  return typeof value === 'string' ? JSON.parse(value) : value;
}
async function waitFor(client, expression, timeout = 30000) {
  const started = Date.now();
  let last = null;
  while (Date.now() - started < timeout) {
    last = await evaluate(client, expression);
    if (last && last.ok) return last;
    await delay(150);
  }
  throw new Error(`Timeout waiting for ${expression}. Last=${JSON.stringify(last)}`);
}
async function setViewport(client, viewport) {
  await client.send('Emulation.setDeviceMetricsOverride', { width: viewport.width, height: viewport.height, deviceScaleFactor: 1, mobile: viewport.mobile });
  await client.send('Emulation.setVisibleSize', { width: viewport.width, height: viewport.height });
}
async function navigate(client, url) {
  await client.send('Page.navigate', { url });
  await waitFor(client, `JSON.stringify({ ok: document.readyState === 'complete' })`, 10000);
  await waitFor(client, `JSON.stringify({ ok: document.body && document.body.innerText.length > 20 })`, 30000);
}
async function setStorage(client, role) {
  await navigate(client, `${baseUrl}/#/`);
  await evaluate(client, `(() => {
    localStorage.setItem('unexamine.accountId', ${JSON.stringify(role.accountId)});
    localStorage.setItem('unexamine.accessToken', ${JSON.stringify(role.accessToken)});
    localStorage.setItem('unexamine.refreshToken', ${JSON.stringify(role.refreshToken)});
    return JSON.stringify({ ok: true });
  })()`);
}
async function capture(client, route, viewport) {
  await navigate(client, `${baseUrl}/?r49=${Date.now()}#${route.path}`);
  if (route.action === 'openAssistant') {
    await waitFor(client, `JSON.stringify({ ok: !!document.querySelector('.workspace-shell') })`, 30000);
    await evaluate(client, `(() => {
      const buttons = Array.from(document.querySelectorAll('button'));
      const headerButtons = Array.from(document.querySelectorAll('.header-actions > button'));
      const button = headerButtons[1] || buttons.find((item) => item.textContent.includes('助手') || item.title.includes('助手'));
      if (button) button.click();
      return JSON.stringify({ ok: !!button });
    })()`);
  }
  await waitFor(client, `JSON.stringify({ ok: ${route.wait} })`, route.action === 'openAssistant' ? 45000 : 30000);
  const raw = await evaluate(client, `(() => {
    const text = document.body.innerText || '';
    const doc = document.documentElement;
    const body = document.body;
    const overflowX = Math.max(0, Math.ceil(Math.max(doc.scrollWidth, body.scrollWidth) - window.innerWidth));
    const expectedSelectors = ${JSON.stringify(route.expectedSelectors)};
    const forbiddenSelectors = ${JSON.stringify(route.forbiddenSelectors || [])};
    return JSON.stringify({
      key: ${JSON.stringify(route.key)},
      role: ${JSON.stringify(route.role)},
      viewport: ${JSON.stringify(viewport.name)},
      url: location.href,
      overflowX,
      expectedMissing: expectedSelectors.filter((selector) => !document.querySelector(selector)),
      forbiddenVisible: forbiddenSelectors.filter((selector) => document.querySelector(selector)),
      hasSuffix: text.includes(${JSON.stringify(suffix)}),
      textSample: text.slice(0, 1500)
    });
  })()`);
  const result = typeof raw === 'string' ? JSON.parse(raw) : raw;
  result.blockers = [];
  if (result.overflowX > 2) result.blockers.push('horizontal overflow');
  if (result.expectedMissing.length) result.blockers.push('expected selector missing');
  if (result.forbiddenVisible.length) result.blockers.push('forbidden selector visible');
  const screenshot = await client.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true });
  fs.writeFileSync(path.join(outDir, `${route.key}-${viewport.name}.png`), Buffer.from(screenshot.data, 'base64'));
  return result;
}
async function run() {
  fs.mkdirSync(outDir, { recursive: true });
  const target = await newTarget();
  const client = await connect(target.webSocketDebuggerUrl);
  await client.send('Page.enable');
  await client.send('Runtime.enable');
  const routes = [
    { role: 'admin', key: 'admin-openapi', path: `/systems/${systemId}/admin/openapi-apps`, wait: `!!document.querySelector('.admin-content') && !!document.querySelector('#openapi-apps')`, expectedSelectors: ['.admin-content', '#openapi-apps'], forbiddenSelectors: ['.runtime-shell'] },
    { role: 'admin', key: 'admin-agent', path: `/systems/${systemId}/admin/agent-config`, wait: `!!document.querySelector('.admin-content') && !!document.querySelector('#agent-config')`, expectedSelectors: ['.admin-content', '#agent-config'], forbiddenSelectors: ['.runtime-shell'] },
    { role: 'normal', key: 'normal-assistant', path: `/systems/${systemId}/dashboard`, action: 'openAssistant', wait: `!!document.querySelector('.assistant-drawer') && !!document.querySelector('.assistant-context') && !!document.querySelector('.assistant-actions')`, expectedSelectors: ['.workspace-shell', '.assistant-drawer', '.assistant-context', '.assistant-actions'], forbiddenSelectors: ['.admin-content'] },
  ];
  const viewports = [
    { name: 'desktop', width: 1440, height: 920, mobile: false },
    { name: 'mobile', width: 390, height: 720, mobile: true },
  ];
  const results = [];
  let currentRole = '';
  for (const viewport of viewports) {
    await setViewport(client, viewport);
    for (const route of routes) {
      if (route.role !== currentRole) {
        currentRole = route.role;
        await setStorage(client, roles[currentRole]);
      }
      results.push(await capture(client, route, viewport));
    }
  }
  client.close();
  const output = { status: results.some((item) => item.blockers.length) ? 'FAIL' : 'PASS', results };
  fs.writeFileSync(path.join(outDir, 'openapi-assistant-integration-browser-audit.json'), JSON.stringify(output, null, 2));
  console.log(JSON.stringify(output));
  if (output.status !== 'PASS') process.exit(1);
}
run().catch((error) => {
  console.error(error && error.stack ? error.stack : String(error));
  process.exit(1);
});
'@
Set-Content -LiteralPath $nodeScript -Encoding UTF8 -Value $nodeCode
$env:R49_BASE_URL = $BaseUrl
$env:R49_CDP_PORT = [string]$debugPort
$env:R49_EVIDENCE_DIR = $EvidenceDir
$env:R49_SYSTEM_ID = $script:SystemId
$env:R49_SUFFIX = $script:Suffix
$env:R49_ADMIN_TOKEN = [string]$AdminLogin.accessToken
$env:R49_ADMIN_REFRESH = [string]$AdminLogin.refreshToken
$env:R49_ADMIN_ACCOUNT_ID = [string]$AdminLogin.profile.accountId
$env:R49_NORMAL_TOKEN = [string]$NormalLogin.accessToken
$env:R49_NORMAL_REFRESH = [string]$NormalLogin.refreshToken
$env:R49_NORMAL_ACCOUNT_ID = [string]$NormalLogin.profile.accountId
$nodeOutput = & 'D:\dev\nodejs24\node.exe' $nodeScript | Out-String
if ($LASTEXITCODE -ne 0) {
    throw "R49 browser audit failed with exit code $LASTEXITCODE. Output: $nodeOutput"
}
$BrowserAuditPath = Join-Path $EvidenceDir 'openapi-assistant-integration-browser-audit.json'
$BrowserAudit = Get-Content -Raw -Encoding UTF8 $BrowserAuditPath | ConvertFrom-Json
Stop-BrowserAudit
Remove-Item -LiteralPath $nodeScript -Force -ErrorAction SilentlyContinue

$script:CleanupResult = Remove-CreatedSystems
$Result = [ordered]@{
    status = 'PASS'
    task = 'REC-P0-049'
    frc = 'FRC-4C OpenAPI, assistant, and integration boundary closure'
    baseUrl = $BaseUrl
    systemId = $script:SystemId
    tenantId = $TenantId
    moduleId = $ModuleId
    openApiAppId = [string]$OpenApiApp.externalAppId
    openApiRecordId = $OpenApiRecordId
    openApiSecretRefId = $SecretRefId
    openApiRotateJobId = [string]$Rotate.jobId
    openApiRotateTaskStatus = [string]$Rotate.task.status
    openApiSuccessLogCount = $OpenApiSuccessLogs.Count
    openApiFailedLogCount = $OpenApiFailedLogs.Count
    readOnlyDeniedStatus = [int]$ReadOnlyDenied.status
    wrongSecretDeniedStatus = [int]$WrongSecretDenied.status
    modelAuthorizationId = [string]$ModelAuth.authorizationId
    platformDeniedStatus = [string]$PlatformDenied.confirmation.status
    agentPolicyId = [string]$AgentPolicy.policyId
    policyPublishPassed = [bool]$PolicyCheck.passed
    adminSessionId = [string]$AdminSession.sessionId
    proposedConfirmationTypes = $ProposedTypes
    writePreviewStatus = [string]$WritePreview.confirmation.status
    writeConfirmedStatus = [string]$ConfirmedWrite.confirmation.status
    writeRejectedStatus = [string]$RejectedWrite.confirmation.status
    draftPreviewStatus = [string]$DraftPreview.confirmation.status
    draftConfirmedStatus = [string]$DraftConfirmed.confirmation.status
    normalSessionId = [string]$NormalSession.sessionId
    normalOpenApiCreateStatus = [int]$NormalOpenApiDenied.status
    normalPolicyCreateStatus = [int]$NormalPolicyDenied.status
    agentAuditLogCount = $AgentAuditRecords.Count
    agentConfirmationAuditCount = $AgentConfirmationAudits.Count
    browserResultCount = @($BrowserAudit.results).Count
    browserOverflowCount = @($BrowserAudit.results | Where-Object { $_.overflowX -gt 2 }).Count
    browserBlockerCount = @($BrowserAudit.results | Where-Object { @($_.blockers).Count -gt 0 }).Count
    browserAuditPath = $BrowserAuditPath
    evidenceDir = $EvidenceDir
    cleanup = $script:CleanupResult
}
Assert-True -Condition ($Result.browserBlockerCount -eq 0 -and $Result.browserOverflowCount -eq 0) -Message 'R49 browser audit reported blockers or horizontal overflow.'
$Result | ConvertTo-Json -Depth 100 | Set-Content -Encoding UTF8 -Path $ResultFile

$summaryLines = @(
    '# R49 OpenAPI Assistant Integration Smoke',
    '',
    "- Status: $($Result.status)",
    "- Base URL: $BaseUrl",
    "- System: $($Result.systemId)",
    "- OpenAPI: app=$($Result.openApiAppId), record=$($Result.openApiRecordId), secretRef=$($Result.openApiSecretRefId), rotateJob=$($Result.openApiRotateJobId)",
    "- OpenAPI logs: success=$($Result.openApiSuccessLogCount), failed=$($Result.openApiFailedLogCount), readonlyDenied=$($Result.readOnlyDeniedStatus), wrongSecretDenied=$($Result.wrongSecretDeniedStatus)",
    "- Agent: policy=$($Result.agentPolicyId), publishCheck=$($Result.policyPublishPassed), platformDenied=$($Result.platformDeniedStatus)",
    "- Assistant confirmations: preview=$($Result.writePreviewStatus), confirm=$($Result.writeConfirmedStatus), reject=$($Result.writeRejectedStatus), draftPreview=$($Result.draftPreviewStatus), draftConfirm=$($Result.draftConfirmedStatus)",
    "- Permission negatives: normalOpenAPI=$($Result.normalOpenApiCreateStatus), normalAgentPolicy=$($Result.normalPolicyCreateStatus)",
    "- Audits: agentLogs=$($Result.agentAuditLogCount), confirmationLogs=$($Result.agentConfirmationAuditCount)",
    "- Browser results: count=$($Result.browserResultCount), overflow=$($Result.browserOverflowCount), blockers=$($Result.browserBlockerCount)",
    '- Browser audit JSON: docs/evidence/recovery/screenshots/r49-openapi-assistant-integration/openapi-assistant-integration-browser-audit.json',
    "- Cleanup: $(@($Result.cleanup) -join ', ')",
    '',
    'This is engineering evidence only. gates.user_script_passed remains false.'
)
$summaryLines | Set-Content -Encoding UTF8 -Path $SummaryFile
$Result | ConvertTo-Json -Depth 100
if ($NoFailExit) {
    exit 0
}
