# examine2 API e2e smoke (PowerShell 5.1+)
$ErrorActionPreference = 'Stop'

$HostUrl = if ($env:EXAMINE_HOST) { $env:EXAMINE_HOST.TrimEnd('/') } else { 'http://127.0.0.1:9999' }
$SmokePass = if ($env:SMOKE_PASS) { $env:SMOKE_PASS } else { 'SmokePass123!' }
$SkipOpenApi = if ($env:SKIP_OPEN_API) { $env:SKIP_OPEN_API } else { '1' }
$Ts = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$AuthMode = if ($env:SMOKE_USER) { 'login' } else { 'register' }
$SmokeUser = if ($env:SMOKE_USER) { $env:SMOKE_USER } else { "smoke_$Ts" }

$script:Token = ''
$script:SystemId = if ($env:SMOKE_SYSTEM_ID) { [long]$env:SMOKE_SYSTEM_ID } else { 0 }
$script:AppId = 0
$script:ModelId = 0
$script:RecordId = 0
$script:PlatId = 0
$script:Pass = 0
$script:Fail = 0
$FieldCode = 'smoke_title'

function Write-Ok($msg) { $script:Pass++; Write-Host "[OK] $msg" -ForegroundColor Green }
function Write-Fail($msg, $detail) { $script:Fail++; Write-Host "[FAIL] $msg - $detail" -ForegroundColor Red }

function Invoke-ExamineApi {
    param(
        [string]$Method = 'GET',
        [string]$Path,
        $Body = $null,
        [switch]$NoAuth,
        [hashtable]$ExtraHeaders
    )
    $uri = "$HostUrl$Path"
    $headers = @{ 'Content-Type' = 'application/json' }
    if (-not $NoAuth -and $script:Token) { $headers['Authorization'] = "Bearer $($script:Token)" }
    if ($ExtraHeaders) { foreach ($k in $ExtraHeaders.Keys) { $headers[$k] = $ExtraHeaders[$k] } }

    $params = @{ Uri = $uri; Method = $Method; Headers = $headers; TimeoutSec = 15 }
    if ($null -ne $Body) { $params['Body'] = ($Body | ConvertTo-Json -Depth 12 -Compress) }
    try {
        $resp = Invoke-RestMethod @params
    } catch {
        $detail = $_.Exception.Message
        if ($_.ErrorDetails.Message) {
            try {
                $errJson = $_.ErrorDetails.Message | ConvertFrom-Json
                if ($errJson.message) { $detail = $errJson.message }
            } catch { $detail = $_.ErrorDetails.Message }
        }
        throw $detail
    }
    if ($null -eq $resp) { return $null }
    if ($resp.PSObject.Properties.Name -contains 'code') {
        if ($resp.code -ne 0) {
            $msg = if ($resp.message) { $resp.message } else { "API code=$($resp.code)" }
            throw $msg
        }
        return $resp.data
    }
    return $resp
}

function Run-Step($name, [scriptblock]$action) {
    try {
        & $action
        Write-Ok $name
    } catch {
        Write-Fail $name $_.Exception.Message
        throw
    }
}

Write-Host "=== examine2 e2e smoke ==="
Write-Host "HOST=$HostUrl USER=$SmokeUser MODE=$AuthMode"

try {
    Run-Step 'ping' { $null = Invoke-ExamineApi -Path '/ping' -NoAuth }

    Run-Step 'auth' {
        if ($AuthMode -eq 'register') {
            $null = Invoke-ExamineApi -Method POST -Path "/v1/platform/auth/register" -NoAuth -Body @{
                username = $SmokeUser; password = $SmokePass
            }
        }
        $login = Invoke-ExamineApi -Method POST -Path "/v1/platform/auth/login" -NoAuth -Body @{
            username = $SmokeUser; password = $SmokePass
        }
        $script:Token = $login.token
        $me = Invoke-ExamineApi -Path "/v1/platform/auth/me"
        $script:PlatId = [long]$me.id
    }

    Run-Step 'system' {
        if (-not $script:SystemId) {
            $perm = Invoke-ExamineApi -Path "/v1/platform/permissions/me"
            $canCreate = ($perm.canCreateSystem -eq 1)
            if ($canCreate) {
                $sys = Invoke-ExamineApi -Method POST -Path "/v1/platform/systems" -Body @{
                    name = "smoke_sys_$Ts"; multiTenantEnabled = 0
                }
                $script:SystemId = [long]$sys.id
            } else {
                $list = @(Invoke-ExamineApi -Path "/v1/platform/systems")
                if ($list.Count -gt 0) { $script:SystemId = [long]$list[0].id }
                else {
                    throw "无 SYSTEM_CREATE 且尚无自建系统。请设置 SMOKE_USER/SMOKE_PASS（平台超管）或 SMOKE_SYSTEM_ID"
                }
            }
        }
        $null = Invoke-ExamineApi -Method POST -Path "/v1/platform/context/enter-system" -Body @{ systemId = $script:SystemId }
    }

    Run-Step 'meta + records' {
        $suffix = $Ts
        $app = Invoke-ExamineApi -Method POST -Path "/v1/system/module/meta/apps/upsert" -Body @{
            appCode = "smoke_app_$suffix"; appName = 'Smoke App'; status = 1
        }
        $script:AppId = [long]$app.id
        $model = Invoke-ExamineApi -Method POST -Path "/v1/system/module/meta/models/upsert" -Body @{
            appId = $script:AppId; modelCode = 'smoke_model'; modelName = 'Smoke Model'; status = 1
        }
        $script:ModelId = [long]$model.id
        $null = Invoke-ExamineApi -Method POST -Path "/v1/system/module/meta/fields/upsert" -Body @{
            appId = $script:AppId; modelId = $script:ModelId; fieldCode = $FieldCode
            fieldName = 'title'; fieldType = 'TEXT'; status = 1
        }
        $created = Invoke-ExamineApi -Method POST -Path "/v1/system/records" -Body @{
            appId = $script:AppId; modelId = $script:ModelId
            data = @{ $FieldCode = "smoke-$suffix" }
        }
        if ($created.recordId) { $script:RecordId = [long]$created.recordId }
        elseif ($created.record.id) { $script:RecordId = [long]$created.record.id }
        $null = Invoke-ExamineApi -Path "/v1/system/records/$($script:RecordId)"
        $null = Invoke-ExamineApi -Method POST -Path "/v1/system/records/query" -Body @{
            appId = $script:AppId; modelId = $script:ModelId; page = 1; limit = 5
            includeFieldCodes = @($FieldCode)
        }
    }

    Run-Step 'rbac' {
        $null = Invoke-ExamineApi -Path "/v1/system/module/rbac/apps/$($script:AppId)/roles"
        $null = Invoke-ExamineApi -Method POST -Path "/v1/system/module/rbac/apps/$($script:AppId)/roles/upsert" -Body @{
            roleCode = 'smoke_role'; roleName = 'Smoke Role'; dataScope = 5; status = 1
        }
        $null = Invoke-ExamineApi -Path "/v1/system/module/rbac/apps/$($script:AppId)/runtime-menus"
    }

    Run-Step 'inbox' {
        $null = Invoke-ExamineApi -Path "/v1/platform/messages?limit=5"
        $null = Invoke-ExamineApi -Path "/v1/system/flow/inbox/tasks/pending?limit=5"
    }

    Run-Step 'flow graph-designer' {
        $temp = Invoke-ExamineApi -Method POST -Path '/v1/system/flow/temps/upsert' -Body @{
            tempCode = "smoke_flow_$Ts"; tempName = 'Smoke Flow'; status = 1
        }
        $tempId = [long]$temp.id
        $ver = Invoke-ExamineApi -Method POST -Path '/v1/system/flow/temp-vers/upsert' -Body @{
            tempId = $tempId; publishStatus = 1; formJson = '{}'
        }
        $verId = [long]$ver.id
        $graphBody = @{
            nodes = @(
                @{ nodeKey = 'start_1'; nodeType = 'start'; nodeName = '开始'; x = 120; y = 120; configJson = '{}' },
                @{ nodeKey = 'approve_1'; nodeType = 'approve'; nodeName = '审批'; x = 320; y = 120; configJson = '{}' },
                @{ nodeKey = 'end_1'; nodeType = 'end'; nodeName = '结束'; x = 520; y = 120; configJson = '{}' }
            )
            edges = @(
                @{ fromNodeKey = 'start_1'; toNodeKey = 'approve_1'; priority = 1; isDefault = 0; cond = '' },
                @{ fromNodeKey = 'approve_1'; toNodeKey = 'end_1'; priority = 1; isDefault = 0; cond = '' }
            )
        }
        $saved = Invoke-ExamineApi -Method POST -Path "/v1/system/flow/temp-vers/$verId/graph-designer" -Body $graphBody
        if (-not $saved.graphJson) { throw 'save graph-designer: graphJson missing' }
        $loaded = Invoke-ExamineApi -Path "/v1/system/flow/temp-vers/$verId/graph-designer"
        $nodeCount = @($loaded.nodes).Count
        if ($nodeCount -lt 3) { throw "load graph-designer: expected >=3 nodes, got $nodeCount" }
        $null = Invoke-ExamineApi -Path "/v1/system/flow/temp-vers/$verId"
        $null = Invoke-ExamineApi -Method POST -Path "/v1/system/flow/temp-vers/$verId/publish"
    }
} catch {
    Write-Host "---"; Write-Host "Passed: $($script:Pass) Failed: $($script:Fail)"
    exit 1
}

Write-Host "---"; Write-Host "Passed: $($script:Pass) Failed: $($script:Fail)"
Write-Host 'SMOKE OK' -ForegroundColor Green
exit 0
