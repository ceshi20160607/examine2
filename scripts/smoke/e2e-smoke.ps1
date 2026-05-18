# examine2 API e2e smoke (PowerShell 5.1+)
$ErrorActionPreference = 'Stop'

$HostUrl = if ($env:EXAMINE_HOST) { $env:EXAMINE_HOST.TrimEnd('/') } else { 'http://127.0.0.1:9999' }
$SmokePass = if ($env:SMOKE_PASS) { $env:SMOKE_PASS } else { 'SmokePass123!' }
$SkipOpenApi = if ($env:SKIP_OPEN_API) { $env:SKIP_OPEN_API } else { '1' }
$Ts = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$AuthMode = if ($env:SMOKE_USER) { 'login' } else { 'register' }
# Fixed default user so re-runs reuse the first-registered super-admin (not a new smoke_<ts> each time)
$SmokeUser = if ($env:SMOKE_USER) { $env:SMOKE_USER } else { 'smoke_e2e' }

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

    $params = @{ Uri = $uri; Method = $Method; Headers = $headers }
    if ($null -ne $Body) {
        $params['Body'] = ($Body | ConvertTo-Json -Depth 12 -Compress)
    }
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
    Run-Step 'ping' {
        $null = Invoke-ExamineApi -Path "/ping" -NoAuth
    }

    Run-Step 'actuator/health' {
        try {
            $null = Invoke-RestMethod -Uri "$HostUrl/actuator/health" -Method Get
        } catch {
            Write-Host "  (actuator skipped)" -ForegroundColor DarkGray
        }
    }

    Run-Step 'auth' {
        if ($AuthMode -eq 'register') {
            try {
                $null = Invoke-ExamineApi -Method POST -Path "/v1/platform/auth/register" -NoAuth -Body @{
                    username = $SmokeUser
                    password = $SmokePass
                }
            } catch {
                if ($_.Exception.Message -notmatch 'exist') { throw }
            }
        }
        $login = Invoke-ExamineApi -Method POST -Path "/v1/platform/auth/login" -NoAuth -Body @{
            username = $SmokeUser
            password = $SmokePass
        }
        $script:Token = $login.token
        $me = Invoke-ExamineApi -Path "/v1/platform/auth/me"
        $script:PlatId = [long]$me.id
    }

    Run-Step 'system' {
        if (-not $script:SystemId) {
            $sysName = "smoke_sys_$Ts"
            try {
                $sys = Invoke-ExamineApi -Method POST -Path "/v1/platform/systems" -Body @{
                    name = $sysName
                    multiTenantEnabled = 0
                }
                $script:SystemId = [long]$sys.id
            } catch {
                $list = @(Invoke-ExamineApi -Path "/v1/platform/systems")
                if ($list.Count -gt 0) {
                    $script:SystemId = [long]$list[0].id
                } else {
                    throw "Cannot create system (need SYSTEM_CREATE). Set SMOKE_USER/SMOKE_PASS or SMOKE_SYSTEM_ID"
                }
            }
        }
        $null = Invoke-ExamineApi -Method POST -Path "/v1/platform/context/enter-system" -Body @{
            systemId = $script:SystemId
        }
    }

    Run-Step 'meta + records' {
        $suffix = $Ts
        $app = Invoke-ExamineApi -Method POST -Path "/v1/system/module/meta/apps/upsert" -Body @{
            appCode = "smoke_app_$suffix"
            appName = 'Smoke App'
            status  = 1
        }
        $script:AppId = [long]$app.id
        $model = Invoke-ExamineApi -Method POST -Path "/v1/system/module/meta/models/upsert" -Body @{
            appId     = $script:AppId
            modelCode = 'smoke_model'
            modelName = 'Smoke Model'
            status    = 1
        }
        $script:ModelId = [long]$model.id
        $null = Invoke-ExamineApi -Method POST -Path "/v1/system/module/meta/fields/upsert" -Body @{
            appId      = $script:AppId
            modelId    = $script:ModelId
            fieldCode  = $FieldCode
            fieldName  = 'Title'
            fieldType  = 'TEXT'
            status     = 1
        }
        $created = Invoke-ExamineApi -Method POST -Path "/v1/system/records" -Body @{
            appId   = $script:AppId
            modelId = $script:ModelId
            data    = @{ $FieldCode = "smoke-$suffix" }
        }
        if ($created.recordId) { $script:RecordId = [long]$created.recordId }
        elseif ($created.record.id) { $script:RecordId = [long]$created.record.id }
        $null = Invoke-ExamineApi -Path "/v1/system/records/$($script:RecordId)"
        $null = Invoke-ExamineApi -Method POST -Path "/v1/system/records/query" -Body @{
            appId             = $script:AppId
            modelId           = $script:ModelId
            page              = 1
            limit             = 5
            includeFieldCodes = @($FieldCode)
        }
        $null = Invoke-ExamineApi -Method POST -Path "/v1/system/records/$($script:RecordId)/update" -Body @{
            data = @{ $FieldCode = "smoke-upd-$suffix" }
        }
    }

    Run-Step 'rbac + runtime menus' {
        $null = Invoke-ExamineApi -Path "/v1/system/module/rbac/apps/$($script:AppId)/roles"
        $null = Invoke-ExamineApi -Method POST -Path "/v1/system/module/rbac/apps/$($script:AppId)/roles/upsert" -Body @{
            roleCode  = 'smoke_role'
            roleName  = 'Smoke Role'
            dataScope = 5
            status    = 1
        }
        $null = Invoke-ExamineApi -Path "/v1/system/module/rbac/apps/$($script:AppId)/runtime-menus"
    }

    Run-Step 'upload' {
        $tmp = [System.IO.Path]::GetTempFileName()
        [System.IO.File]::WriteAllText($tmp, "smoke-upload-$Ts")
        try {
            if (Get-Command curl.exe -ErrorAction SilentlyContinue) {
                $raw = curl.exe -sS -X POST "$HostUrl/v1/system/uploads" `
                    -H "Authorization: Bearer $($script:Token)" `
                    -F "file=@$tmp;filename=smoke.txt"
                $resp = $raw | ConvertFrom-Json
                if ($resp.code -ne 0) { throw $resp.message }
            } else {
                Write-Host "  (curl.exe not found, skip upload)" -ForegroundColor DarkGray
            }
        } finally {
            Remove-Item $tmp -Force -ErrorAction SilentlyContinue
        }
    }

    Run-Step 'inbox + flow pending' {
        $null = Invoke-ExamineApi -Path "/v1/platform/messages?limit=5"
        $null = Invoke-ExamineApi -Path "/v1/platform/todos?limit=5"
        $null = Invoke-ExamineApi -Path "/v1/platform/cc?limit=5"
        $null = Invoke-ExamineApi -Path "/v1/system/flow/inbox/tasks/pending?limit=5"
    }

    if ($SkipOpenApi -ne '1' -and $env:OPEN_AK -and $env:OPEN_SK) {
        Run-Step 'open api records query' {
            $acting = if ($env:OPEN_ACTING_PLAT_ID) { $env:OPEN_ACTING_PLAT_ID } else { $script:PlatId }
            $target = if ($env:OPEN_TARGET_SYSTEM_ID) { $env:OPEN_TARGET_SYSTEM_ID } else { $script:SystemId }
            $hdr = @{
                'X-Access-Key'       = $env:OPEN_AK
                'X-Secret'           = $env:OPEN_SK
                'X-Acting-Plat-Id'   = "$acting"
                'X-Target-System-Id' = "$target"
            }
            $null = Invoke-ExamineApi -Method POST -Path "/v1/open/records/query" -NoAuth -ExtraHeaders $hdr -Body @{
                appId   = $script:AppId
                modelId = $script:ModelId
                page    = 1
                limit   = 5
            }
        }
    } else {
        Write-Host "[SKIP] open api" -ForegroundColor DarkGray
    }
} catch {
    Write-Host '---'
    Write-Host "Passed: $($script:Pass)  Failed: $($script:Fail)"
    Write-Host 'SMOKE FAILED' -ForegroundColor Red
    exit 1
}

Write-Host '---'
Write-Host "Passed: $($script:Pass)  Failed: $($script:Fail)"
Write-Host 'SMOKE OK' -ForegroundColor Green
exit 0
