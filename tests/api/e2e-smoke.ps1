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
$script:FlowInstanceId = 0
$script:FlowTaskId = 0
$script:PlatId = 0
$script:OpenAccessKey = ''
$script:OpenSecret = ''
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

function Invoke-OpenExamineApi {
    param(
        [string]$Method = 'POST',
        [string]$Path,
        $Body = $null,
        [string]$AccessKey,
        [string]$Secret,
        [long]$ActingPlatId,
        [long]$TargetSystemId,
        [long]$TargetTenantId = 0
    )
    if (-not $AccessKey -or -not $Secret) { throw 'open api credentials missing' }
    $uri = "$HostUrl$Path"
    $headers = @{
        'Content-Type'     = 'application/json'
        'X-Access-Key'     = $AccessKey
        'X-Secret'         = $Secret
        'X-Acting-Plat-Id' = "$ActingPlatId"
        'X-Target-System-Id' = "$TargetSystemId"
        'X-Target-Tenant-Id' = "$TargetTenantId"
    }
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
            $list = @(Invoke-ExamineApi -Path "/v1/platform/systems")
            if ($list.Count -gt 0) {
                $script:SystemId = [long]$list[0].id
            } else {
                $perm = Invoke-ExamineApi -Path "/v1/platform/permissions/me"
                $canCreate = ($perm.canCreateSystem -eq 1)
                if (-not $canCreate) {
                    throw "无 SYSTEM_CREATE 且尚无自建系统。请设置 SMOKE_USER/SMOKE_PASS（平台超管）或 SMOKE_SYSTEM_ID"
                }
                $sys = Invoke-ExamineApi -Method POST -Path "/v1/platform/systems" -Body @{
                    name = "smoke_sys_$Ts"; multiTenantEnabled = 0
                }
                $script:SystemId = [long]$sys.id
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
                @{ nodeKey = 'start_1'; nodeType = 'start'; nodeName = 'Start'; x = 120; y = 120; configJson = '{}' },
                @{ nodeKey = 'approve_1'; nodeType = 'approve'; nodeName = 'Approve'; x = 320; y = 120; configJson = '{}' },
                @{ nodeKey = 'end_1'; nodeType = 'end'; nodeName = 'End'; x = 520; y = 120; configJson = '{}' }
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
        $script:FlowTempId = $tempId
        $script:FlowVerId = $verId
    }

    Run-Step 'platform permissions' {
        $perm = Invoke-ExamineApi -Path '/v1/platform/permissions/me'
        if ($null -eq $perm.platPermCodes) { throw 'platPermCodes missing' }
    }

    Run-Step 'meta catalog' {
        $null = Invoke-ExamineApi -Path '/v1/system/module/meta/apps'
        $null = Invoke-ExamineApi -Path '/v1/system/module/meta/field-types'
        $null = Invoke-ExamineApi -Path "/v1/system/module/meta/apps/$($script:AppId)/models"
        $null = Invoke-ExamineApi -Path "/v1/system/module/meta/models/$($script:ModelId)/fields"
    }

    Run-Step 'record update + history' {
        $suffix = $Ts
        $null = Invoke-ExamineApi -Method POST -Path "/v1/system/records/$($script:RecordId)/update" -Body @{
            appId = $script:AppId; modelId = $script:ModelId
            data = @{ $FieldCode = "smoke-upd-$suffix" }
        }
        $null = Invoke-ExamineApi -Path "/v1/system/records/$($script:RecordId)/history"
    }

    Run-Step 'dept + module auth' {
        $null = Invoke-ExamineApi -Path "/v1/system/module/depts/apps/$($script:AppId)"
        $null = Invoke-ExamineApi -Path '/v1/system/auth/permissions'
    }

    Run-Step 'flow temps list' {
        $null = Invoke-ExamineApi -Path '/v1/system/flow/temps/page?page=1&size=10'
        if ($script:FlowTempId) {
            $null = Invoke-ExamineApi -Path "/v1/system/flow/temps/$($script:FlowTempId)"
            $null = Invoke-ExamineApi -Path "/v1/system/flow/temp-vers/page?tempId=$($script:FlowTempId)&page=1&size=5"
        }
    }

    Run-Step 'flow inbox cc' {
        $null = Invoke-ExamineApi -Path '/v1/system/flow/inbox/cc?limit=5'
    }

    Run-Step 'dict + pages + list-views' {
        $dict = Invoke-ExamineApi -Method POST -Path "/v1/system/module/dicts/apps/$($script:AppId)/upsert" -Body @{
            dictCode = "smoke_dict_$Ts"; dictName = 'Smoke Dict'; status = 1
        }
        $dictId = [long]$dict.id
        $null = Invoke-ExamineApi -Path "/v1/system/module/dicts/apps/$($script:AppId)"
        $null = Invoke-ExamineApi -Method POST -Path "/v1/system/module/dicts/$dictId/items/upsert" -Body @{
            itemValue = 'a'; itemLabel = 'A'; sortNo = 1; status = 1
        }
        $null = Invoke-ExamineApi -Path "/v1/system/module/dicts/$dictId/items"
        $null = Invoke-ExamineApi -Path "/v1/system/module/pages/apps/$($script:AppId)"
        $null = Invoke-ExamineApi -Path "/v1/system/module/list-views/models/$($script:ModelId)"
        $null = Invoke-ExamineApi -Path "/v1/system/module/meta/apps/$($script:AppId)/relations"
    }

    Run-Step 'flow-bindings + instances' {
        $null = Invoke-ExamineApi -Path "/v1/system/module/flow-bindings/apps/$($script:AppId)/models/$($script:ModelId)"
        $null = Invoke-ExamineApi -Path '/v1/system/module/flow-bindings/flow-temps'
        $null = Invoke-ExamineApi -Path '/v1/system/flow/instances/page?page=1&size=5'
        $null = Invoke-ExamineApi -Path '/v1/system/flow/tasks/page?page=1&size=5'
    }

    Run-Step 'flow lifecycle' {
        if (-not $script:FlowTempId) { throw 'FlowTempId missing (graph-designer step)' }
        $temp = Invoke-ExamineApi -Path "/v1/system/flow/temps/$($script:FlowTempId)"
        $defCode = $temp.tempCode
        if (-not $defCode) { throw 'flow tempCode missing' }
        $start = Invoke-ExamineApi -Method POST -Path '/v1/system/flow/instances/start' -Body @{
            defCode = $defCode; title = "smoke-inst-$Ts"; bizType = 'smoke'; bizId = "biz-$Ts"
        }
        $script:FlowInstanceId = [long]$start.instanceId
        $script:FlowTaskId = [long]$start.taskId
        $null = Invoke-ExamineApi -Path "/v1/system/flow/instances/$($script:FlowInstanceId)"
        $null = Invoke-ExamineApi -Path "/v1/system/flow/instances/$($script:FlowInstanceId)/tasks"
        $null = Invoke-ExamineApi -Path "/v1/system/flow/instances/$($script:FlowInstanceId)/traces"
        $null = Invoke-ExamineApi -Path "/v1/system/flow/instances/$($script:FlowInstanceId)/actions"
        $null = Invoke-ExamineApi -Method POST -Path "/v1/system/flow/instances/$($script:FlowInstanceId)/tasks/$($script:FlowTaskId)/claim"
        $null = Invoke-ExamineApi -Method POST -Path "/v1/system/flow/instances/$($script:FlowInstanceId)/tasks/$($script:FlowTaskId)/approve" -Body @{ commentText = 'smoke ok' }
    }

    Run-Step 'flow reject' {
        if (-not $script:FlowTempId) { throw 'FlowTempId missing' }
        $temp = Invoke-ExamineApi -Path "/v1/system/flow/temps/$($script:FlowTempId)"
        $defCode = $temp.tempCode
        $start = Invoke-ExamineApi -Method POST -Path '/v1/system/flow/instances/start' -Body @{
            defCode = $defCode; title = "smoke-rej-$Ts"; bizType = 'smoke'; bizId = "biz-rej-$Ts"
        }
        $instId = [long]$start.instanceId
        $taskId = [long]$start.taskId
        $null = Invoke-ExamineApi -Method POST -Path "/v1/system/flow/instances/$instId/tasks/$taskId/claim"
        $null = Invoke-ExamineApi -Method POST -Path "/v1/system/flow/instances/$instId/tasks/$taskId/reject" -Body @{ commentText = 'smoke reject' }
    }

    Run-Step 'rbac menus members perms' {
        $role = Invoke-ExamineApi -Method POST -Path "/v1/system/module/rbac/apps/$($script:AppId)/roles/upsert" -Body @{
            roleCode = "smoke_role2_$Ts"; roleName = 'Smoke Role2'; dataScope = 5; status = 1
        }
        $roleId = [long]$role.id
        $menus = @(Invoke-ExamineApi -Path "/v1/system/module/rbac/apps/$($script:AppId)/menus")
        $menuIds = @()
        foreach ($m in $menus) { if ($m.id) { $menuIds += [long]$m.id } }
        if ($menuIds.Count -gt 0) {
            $null = Invoke-ExamineApi -Method POST -Path '/v1/system/module/rbac/roles/menu-perms/set' -Body @{
                roleId = $roleId; menuIds = $menuIds; permLevel = 1
            }
            $null = Invoke-ExamineApi -Path "/v1/system/module/rbac/roles/$roleId/menu-perms"
        }
        $null = Invoke-ExamineApi -Path "/v1/system/module/rbac/apps/$($script:AppId)/members"
        $null = Invoke-ExamineApi -Path '/v1/system/module/rbac/account-search?keyword=ad'
    }

    Run-Step 'pages runtime + list-view cols' {
        $page = Invoke-ExamineApi -Method POST -Path '/v1/system/module/pages/upsert' -Body @{
            appId = $script:AppId; pageCode = "smoke_page_$Ts"; pageName = 'Smoke Page'
            pageType = 'list'; configJson = (@{ modelId = $script:ModelId } | ConvertTo-Json -Compress); status = 1
        }
        $pageId = [long]$page.id
        $null = Invoke-ExamineApi -Path "/v1/system/module/pages/$pageId/runtime"
        $null = Invoke-ExamineApi -Path "/v1/system/module/pages/$pageId/detail"
        $lv = Invoke-ExamineApi -Method POST -Path '/v1/system/module/list-views/upsert' -Body @{
            appId = $script:AppId; modelId = $script:ModelId; viewCode = "smoke_lv_$Ts"
            viewName = 'Smoke LV'; status = 1
        }
        $viewId = [long]$lv.id
        $null = Invoke-ExamineApi -Path "/v1/system/module/list-views/$viewId/cols"
        $null = Invoke-ExamineApi -Path '/v1/system/module/meta/actions'
        $null = Invoke-ExamineApi -Path '/v1/system/auth/perm-preview?uri=/v1/system/records'
    }

    Run-Step 'uploads + export-jobs + platform apps' {
        $null = Invoke-ExamineApi -Path '/v1/system/uploads/page?page=1&size=5'
        $null = Invoke-ExamineApi -Path '/v1/system/module/export-jobs/page?page=1&size=5'
        $null = Invoke-ExamineApi -Path '/v1/platform/apps'
        $client = Invoke-ExamineApi -Method POST -Path '/v1/platform/apps' -Body @{
            clientCode = "smoke_client_$Ts"; clientName = 'Smoke Client'
        }
        $clientId = [long]$client.clientId
        $null = Invoke-ExamineApi -Path "/v1/platform/apps/$clientId"
        $rotated = Invoke-ExamineApi -Method POST -Path "/v1/platform/apps/$clientId/rotate-secret" -Body @{}
        if (-not $rotated.secret) { throw 'rotate platform app secret missing' }
        $script:OpenAccessKey = [string]$rotated.accessKey
        $script:OpenSecret = [string]$rotated.secret
        $null = Invoke-ExamineApi -Path "/v1/system/module/exports/models/$($script:ModelId)/tpls"
    }

    if ($SkipOpenApi -ne '1') {
        Run-Step 'open api records' {
            if (-not $script:OpenAccessKey -or -not $script:OpenSecret) {
                throw 'OPEN AK/SK missing (run platform apps step first or set OPEN_AK/OPEN_SK)'
            }
            $ak = if ($env:OPEN_AK) { $env:OPEN_AK } else { $script:OpenAccessKey }
            $sk = if ($env:OPEN_SK) { $env:OPEN_SK } else { $script:OpenSecret }
            $acting = if ($env:OPEN_ACTING_PLAT_ID) { [long]$env:OPEN_ACTING_PLAT_ID } else { $script:PlatId }
            $targetSys = if ($env:OPEN_TARGET_SYSTEM_ID) { [long]$env:OPEN_TARGET_SYSTEM_ID } else { $script:SystemId }
            $null = Invoke-OpenExamineApi -Method POST -Path '/v1/open/records/query' -Body @{
                appId = $script:AppId; modelId = $script:ModelId; page = 1; limit = 5
            } -AccessKey $ak -Secret $sk -ActingPlatId $acting -TargetSystemId $targetSys
        }
    }
} catch {
    Write-Host "---"; Write-Host "Passed: $($script:Pass) Failed: $($script:Fail)"
    exit 1
}

Write-Host "---"; Write-Host "Passed: $($script:Pass) Failed: $($script:Fail)"
Write-Host 'SMOKE OK' -ForegroundColor Green
exit 0
