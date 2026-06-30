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
    $jsonBody = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 50 -Compress }
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
        throw "API failed: $Method $Path -> $($response | ConvertTo-Json -Depth 20 -Compress)"
    }
    return $response.data
}

function Invoke-ExpectedForbidden {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [object]$Body = $null,
        [hashtable]$Headers = @{}
    )
    $uri = "$BaseUrl$Path"
    $jsonBody = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 50 -Compress }
    try {
        if ($null -eq $jsonBody) {
            $null = Invoke-RestMethod -Method $Method -Uri $uri -Headers $Headers -TimeoutSec 30
        } else {
            $null = Invoke-RestMethod -Method $Method -Uri $uri -Headers $Headers `
                -ContentType 'application/json; charset=utf-8' -Body $jsonBody -TimeoutSec 30
        }
        throw "Expected forbidden response but request succeeded: $Method $Path"
    } catch {
        if (-not $_.Exception.Response) {
            throw
        }
        $status = [int]$_.Exception.Response.StatusCode
        if ($status -ne 403) {
            $reader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
            $body = $reader.ReadToEnd()
            throw "Expected HTTP 403 but got HTTP ${status}: $body"
        }
        return @{ status = $status }
    }
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
    foreach ($systemId in @($script:SystemId, $script:NormalOwnedSystemId)) {
        if ([string]::IsNullOrWhiteSpace($systemId)) {
            continue
        }
        try {
            $deleted = Invoke-Api -Method 'Delete' -Path "/api/v1/platform/systems/$systemId" -Headers $script:AdminHeaders -Body @{
                reason = 'recovery-r24 page designer cleanup'
                impactScope = 'created_by_current_script'
                idempotencyKey = "cleanup-page-r24-$script:Suffix-$systemId"
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

$script:Suffix = "$(Get-Date -Format 'MMddHHmmssfff')_$((New-Guid).ToString('N').Substring(0, 6))"
$Password = 'Aa123456!'
$script:SystemId = $null
$script:NormalOwnedSystemId = $null
$script:AdminHeaders = $null
$script:CleanupResult = @('SKIPPED')

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
    systemName = "R24 Page Designer $script:Suffix"
    systemCode = "r24_page_$script:Suffix"
    tenantMode = 1
    templateCode = 'default'
}
$script:SystemId = [string]$System.systemId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($script:SystemId)) -Message 'Created system did not return systemId.'

$SwitchOptions = Invoke-Api -Method 'Get' -Path '/api/v1/platform/system-switch/options' -Headers $script:AdminHeaders
$SystemOption = @($SwitchOptions | Where-Object { [string]$_.systemId -eq $script:SystemId }) | Select-Object -First 1
$TenantId = [string]$SystemOption.tenantId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($TenantId)) -Message 'Created system tenantId was not found.'
$null = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $script:AdminHeaders -Body @{
    systemId = $script:SystemId
    tenantId = $TenantId
    reason = 'recovery-r24 page designer setup'
}

$Module = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/modules" -Headers $script:AdminHeaders -Body @{
    moduleCode = "r24_page_module_$script:Suffix"
    name = "R24 Page Module $script:Suffix"
    status = 1
    description = 'R24 page designer smoke module'
}
$ModuleId = [string]$Module.moduleId
Assert-True -Condition (-not [string]::IsNullOrWhiteSpace($ModuleId)) -Message 'Created module did not return moduleId.'

$PageCode = "main_$script:Suffix"
$PageName = "R24 Designer Page $script:Suffix"
$PagePayload = @{
    pageCode = $PageCode
    pageName = $PageName
    pageType = 'MODULE_LIST'
    route = "/systems/$script:SystemId/modules/$ModuleId"
    layoutMode = 'left-list-right-detail'
    components = @(
        @{ componentCode = 'toolbar'; componentType = 'SHORTCUT'; title = 'Page Actions'; dataSource = 'MODULE_ACTIONS'; sort = 10; visible = $true },
        @{ componentCode = 'list'; componentType = 'LIST'; title = 'Runtime Records'; dataSource = 'RUNTIME_RECORDS'; sort = 20; visible = $true },
        @{ componentCode = 'detail'; componentType = 'DETAIL'; title = 'Record Detail'; dataSource = 'RECORD_DETAIL'; boundFieldCode = 'title'; sort = 30; visible = $true },
        @{ componentCode = 'chart'; componentType = 'CHART'; title = 'Status Chart'; dataSource = 'RUNTIME_STATISTICS'; boundFieldCode = 'status'; sort = 40; visible = $true }
    )
    visibleRoleIds = @()
    changeReason = 'recovery-r24 page designer persisted draft'
}

$Saved = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/modules/$ModuleId/pages" -Headers $script:AdminHeaders -Body $PagePayload
Assert-True -Condition ($Saved.pageCode -eq $PageCode -and $Saved.pageName -eq $PageName) -Message 'Saved page did not echo page code/name.'
Assert-True -Condition (@($Saved.components).Count -eq 4) -Message 'Saved page did not return four components.'
Assert-True -Condition ($Saved.publishStatus -eq 'DRAFT') -Message 'Saved page should be DRAFT before publish.'

$AdminReadback = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$script:SystemId/modules/$ModuleId/pages" -Headers $script:AdminHeaders
$ReadbackPage = @($AdminReadback | Where-Object { $_.pageCode -eq $PageCode }) | Select-Object -First 1
Assert-True -Condition ($null -ne $ReadbackPage) -Message 'Admin page list did not read back saved page.'

$PublishCheck = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/modules/$ModuleId/pages/$PageCode/publish-check" -Headers $script:AdminHeaders
Assert-True -Condition ($PublishCheck.passed -eq $true) -Message "Page publish-check did not pass: $($PublishCheck | ConvertTo-Json -Depth 20 -Compress)"

$Published = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/modules/$ModuleId/pages/$PageCode/publish" -Headers $script:AdminHeaders -Body @{
    reason = 'recovery-r24 page designer publish'
    idempotencyKey = "publish-page-$script:Suffix"
}
Assert-True -Condition ($Published.result -eq 'PUBLISHED_MODULE_PAGE') -Message 'Page publish did not return PUBLISHED_MODULE_PAGE.'

$RuntimeReadback = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$script:SystemId/runtime/modules/$ModuleId/pages/$PageCode" -Headers $script:AdminHeaders
Assert-True -Condition ($RuntimeReadback.pageName -eq $PageName) -Message 'Admin runtime page read did not return published page name.'
Assert-True -Condition ($RuntimeReadback.publishStatus -eq 'PUBLISHED') -Message 'Runtime page read did not return PUBLISHED status.'
Assert-True -Condition (@($RuntimeReadback.components).Count -eq 4) -Message 'Runtime page read did not return published components.'

$NormalLoginName = "r24_page_member_$script:Suffix"
$NormalRegister = Invoke-Api -Method 'Post' -Path '/api/v1/auth/register-with-system' -Body @{
    accountName = $NormalLoginName
    mobile = "19$((Get-Date).ToString('HHmmssfff'))"
    email = "r24_page_member_$script:Suffix@example.com"
    password = $Password
    systemName = "R24 Page Owned $script:Suffix"
    systemCode = "r24_page_owned_$script:Suffix"
    tenantMode = 1
    templateCode = 'blank'
}
$script:NormalOwnedSystemId = [string]$NormalRegister.systemId

$Member = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/members" -Headers $script:AdminHeaders -Body @{
    deptId = $null
    memberName = "R24 Page Runtime Member $script:Suffix"
    employeeNo = "R24PG$((Get-Date).ToString('HHmmssfff'))"
    mobile = "18$((Get-Date).ToString('HHmmssfff'))"
    email = "r24_page_runtime_member_$script:Suffix@example.com"
    status = 1
    roleIds = @()
}
$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$script:SystemId/members/$($Member.systemMemberId)/bind-account" -Headers $script:AdminHeaders -Body @{
    accountId = $null
    loginName = $NormalLoginName
    bindMode = 'BIND'
}

$NormalLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = $NormalLoginName
    password = $Password
    loginTarget = 'PLATFORM'
}
$NormalHeaders = @{ Authorization = "Bearer $($NormalLogin.accessToken)" }
$null = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $NormalHeaders -Body @{
    systemId = $script:SystemId
    tenantId = $TenantId
    reason = 'recovery-r24 normal member runtime page read'
}
$NormalRuntimeRead = Invoke-Api -Method 'Get' -Path "/api/v1/systems/$script:SystemId/runtime/modules/$ModuleId/pages/$PageCode" -Headers $NormalHeaders
Assert-True -Condition ($NormalRuntimeRead.pageName -eq $PageName) -Message 'Normal member could not read published runtime page.'
$ForbiddenWrite = Invoke-ExpectedForbidden -Method 'Post' -Path "/api/v1/systems/$script:SystemId/modules/$ModuleId/pages" -Headers $NormalHeaders -Body $PagePayload

$script:CleanupResult = Remove-CreatedSystems

$Result = [ordered]@{
    status = 'PASS'
    task = 'REC-P0-027'
    subtask = 'page-designer-api-runtime-closure'
    baseUrl = $BaseUrl
    systemId = $script:SystemId
    tenantId = $TenantId
    moduleId = $ModuleId
    pageCode = $PageCode
    pageName = $PageName
    savedStatus = $Saved.publishStatus
    publishCheckPassed = $PublishCheck.passed
    publishResult = $Published.result
    runtimeReadbackStatus = $RuntimeReadback.publishStatus
    runtimeComponentCount = @($RuntimeReadback.components).Count
    normalRuntimeReadPageName = $NormalRuntimeRead.pageName
    forbiddenWriteStatus = $ForbiddenWrite.status
    cleanup = $script:CleanupResult
}
$ResultFile = Join-Path (Get-Location) 'docs\evidence\recovery\r24-page-designer-result.json'
$Result | ConvertTo-Json -Depth 50 | Set-Content -LiteralPath $ResultFile -Encoding UTF8
$Result | ConvertTo-Json -Depth 50
