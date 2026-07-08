param(
    [string]$BaseUrl = 'http://127.0.0.1:18131',
    [switch]$NoFailExit
)

$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$EvidenceDir = Join-Path $RepoRoot 'docs/evidence/recovery'
$BrowserDir = Join-Path $EvidenceDir 'screenshots/r90-org-member-role-binding-first-use'
$ResultPath = Join-Path $EvidenceDir 'r90-org-member-role-binding-first-use-result.json'
$SummaryPath = Join-Path $EvidenceDir 'r90-org-member-role-binding-first-use-2026-07-07.md'
$BrowserScriptPath = Join-Path $RepoRoot 'scripts/recovery-r90-browser-audit.js'
New-Item -ItemType Directory -Force -Path $EvidenceDir | Out-Null
New-Item -ItemType Directory -Force -Path $BrowserDir | Out-Null

function Invoke-Api {
    param([string]$Method, [string]$Path, [object]$Body = $null, [hashtable]$Headers = @{})
    $jsonBody = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 100 -Compress }
    try {
        if ($null -eq $jsonBody) {
            $response = Invoke-RestMethod -Method $Method -Uri "$BaseUrl$Path" -Headers $Headers -TimeoutSec 45
        } else {
            $response = Invoke-RestMethod -Method $Method -Uri "$BaseUrl$Path" -Headers $Headers -ContentType 'application/json; charset=utf-8' -Body $jsonBody -TimeoutSec 45
        }
    } catch {
        $status = 0
        $body = $_.Exception.Message
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
            try {
                $reader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
                $body = $reader.ReadToEnd()
            } catch {}
        }
        throw "API failed: $Method $Path -> HTTP $status $body"
    }
    if ($response.code -ne 'SUCCESS') { throw "API failed: $Method $Path -> $($response | ConvertTo-Json -Depth 30 -Compress)" }
    return $response.data
}

function Invoke-ExpectedDenied {
    param([string]$Method, [string]$Path, [object]$Body = $null, [hashtable]$Headers = @{})
    try {
        $null = Invoke-Api -Method $Method -Path $Path -Body $Body -Headers $Headers
    } catch {
        if ($_.Exception.Message -match 'HTTP 401|HTTP 403|PERMISSION_DENIED|没有系统后台管理权限') {
            return @{ status = 'DENIED'; path = $Path; detail = $_.Exception.Message }
        }
        throw
    }
    throw "Expected denied request but it succeeded: $Method $Path"
}

function Add-Check([System.Collections.Generic.List[object]]$Checks, [string]$Area, [string]$Name, [bool]$Passed, [string]$Detail) {
    $Checks.Add([pscustomobject]@{ area = $Area; name = $Name; passed = $Passed; detail = $Detail }) | Out-Null
}

function Read-DeployedAssetText {
    try {
        $index = (Invoke-WebRequest -Uri "$BaseUrl/" -UseBasicParsing -TimeoutSec 30).Content
        $assetMatches = [regex]::Matches($index, 'src="(?<src>/assets/[^""<>]+\.js)"')
        if ($assetMatches.Count -eq 0) { return '' }
        $texts = New-Object System.Collections.Generic.List[string]
        foreach ($match in $assetMatches) {
            $src = $match.Groups['src'].Value
            $texts.Add((Invoke-WebRequest -Uri "$BaseUrl$src" -UseBasicParsing -TimeoutSec 30).Content) | Out-Null
        }
        return ($texts -join "`n")
    } catch {
        return ''
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
    foreach ($candidate in $paths) {
        if ($candidate -and (Test-Path -LiteralPath $candidate)) { return $candidate }
    }
    throw 'Chrome or Edge executable was not found.'
}

$checks = [System.Collections.Generic.List[object]]::new()
$stamp = Get-Date -Format 'yyyyMMddHHmmss'
$password = 'Aa123456!'
$adminLoginName = "r90_admin_$stamp"
$normalLoginName = "r90_normal_$stamp"
$adminSystemCode = "r90_sys_$stamp"
$normalSystemCode = "r90_own_$stamp"
$chromeProcess = $null
$chromeProfile = $null
$createdSystems = New-Object System.Collections.Generic.List[string]
$status = 'PASS'
$errorMessage = $null

try {
    $health = Invoke-Api -Method Get -Path '/api/v1/health'
    Add-Check $checks 'release' 'release health is reachable' ($health.status -eq 'UP') "status=$($health.status)"

    $adminReg = Invoke-Api -Method Post -Path '/api/v1/auth/register-with-system' -Body @{
        accountName = $adminLoginName
        mobile = "139$($stamp.Substring($stamp.Length - 8))"
        email = "$adminLoginName@example.test"
        password = $password
        systemName = "R90 成员交付系统 $stamp"
        systemCode = $adminSystemCode
        tenantMode = 1
        templateCode = 'blank'
    }
    $systemId = [string]$adminReg.systemId
    $createdSystems.Add($systemId) | Out-Null
    $adminHeaders = @{ Authorization = "Bearer $($adminReg.accessToken)" }
    $adminSwitch = Invoke-Api -Method Post -Path '/api/v1/platform/system-switch' -Headers $adminHeaders -Body @{ systemId = $systemId; tenantId = $null; reason = 'R90 org member binding admin setup' }
    Add-Check $checks 'setup' 'registered admin owns new system' (@($adminSwitch.effectiveRoleIds) -contains 'SYSTEM_SUPER_ADMIN') "system=$systemId roles=$(@($adminSwitch.effectiveRoleIds) -join ',')"

    $normalReg = Invoke-Api -Method Post -Path '/api/v1/auth/register-with-system' -Body @{
        accountName = $normalLoginName
        mobile = "138$($stamp.Substring($stamp.Length - 8))"
        email = "$normalLoginName@example.test"
        password = $password
        systemName = "R90 普通账号自有系统 $stamp"
        systemCode = $normalSystemCode
        tenantMode = 1
        templateCode = 'blank'
    }
    $createdSystems.Add([string]$normalReg.systemId) | Out-Null
    $normalHeaders = @{ Authorization = "Bearer $($normalReg.accessToken)" }

    $dept = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/org/departments" -Headers $adminHeaders -Body @{
        parentId = '0'
        deptCode = "r90_dept_$stamp"
        deptName = "R90 业务部 $stamp"
        sortOrder = 10
        status = 1
    }
    $role = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/roles" -Headers $adminHeaders -Body @{
        roleName = "R90 运行成员 $stamp"
        roleCode = "R90_RUNTIME_$stamp"
        roleType = 'CUSTOM'
        status = 1
        description = 'R90 member binding evidence role'
    }
    $member = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/members" -Headers $adminHeaders -Body @{
        deptId = [string]$dept.deptId
        memberName = "R90 普通成员 $stamp"
        employeeNo = "R90-$stamp"
        mobile = "137$($stamp.Substring($stamp.Length - 8))"
        email = "member-$stamp@example.test"
        status = 1
        roleIds = @()
    }
    $binding = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/members/$($member.systemMemberId)/bind-account" -Headers $adminHeaders -Body @{
        loginName = $normalLoginName
        bindMode = 'BIND'
    }
    $assigned = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/roles/$($role.roleId)/assign-members" -Headers $adminHeaders -Body @{
        systemMemberIds = @([string]$member.systemMemberId)
        accountIds = @()
    }
    $membersAfter = Invoke-Api -Method Get -Path "/api/v1/systems/$systemId/members?pageNo=1&pageSize=50" -Headers $adminHeaders
    $boundMember = @($membersAfter.records | Where-Object { [string]$_.systemMemberId -eq [string]$member.systemMemberId }) | Select-Object -First 1
    Add-Check $checks 'api' 'member binding is readable' ($boundMember.bindingStatus -eq 'BOUND') "member=$($member.systemMemberId) binding=$($binding.bindingId) status=$($boundMember.bindingStatus)"
    Add-Check $checks 'api' 'member role assignment is readable' (@($boundMember.roleIds) -contains [string]$role.roleId) "role=$($role.roleId) memberRoles=$(@($boundMember.roleIds) -join ',') assigned=$($assigned.assignedCount)"

    $normalSwitch = Invoke-Api -Method Post -Path '/api/v1/platform/system-switch' -Headers $normalHeaders -Body @{ systemId = $systemId; tenantId = $null; reason = 'R90 normal switch after admin binding' }
    Add-Check $checks 'switch' 'bound normal account can switch into target system' ([string]$normalSwitch.systemMemberId -eq [string]$member.systemMemberId) "switchMember=$($normalSwitch.systemMemberId) binding=$($normalSwitch.accountMemberBindingId)"
    Add-Check $checks 'switch' 'normal switch context includes assigned role' ((@($normalSwitch.effectiveRoleIds) -contains [string]$role.roleId) -or (@($normalSwitch.effectiveRoleIds) -contains [string]$role.roleCode)) "effectiveRoles=$(@($normalSwitch.effectiveRoleIds) -join ',')"

    $normalMemberDenied = Invoke-ExpectedDenied -Method Get -Path "/api/v1/systems/$systemId/members?pageNo=1&pageSize=20" -Headers $normalHeaders
    $normalDeptDenied = Invoke-ExpectedDenied -Method Post -Path "/api/v1/systems/$systemId/org/departments" -Headers $normalHeaders -Body @{ deptCode = "deny_$stamp"; deptName = 'Denied'; status = 1 }
    $normalRoleDenied = Invoke-ExpectedDenied -Method Post -Path "/api/v1/systems/$systemId/roles" -Headers $normalHeaders -Body @{ roleName = 'Denied'; roleCode = "DENY_$stamp"; roleType = 'CUSTOM'; status = 1 }
    Add-Check $checks 'permission' 'normal member cannot read or mutate org role admin APIs' ($normalMemberDenied.status -eq 'DENIED' -and $normalDeptDenied.status -eq 'DENIED' -and $normalRoleDenied.status -eq 'DENIED') "member=$($normalMemberDenied.status), dept=$($normalDeptDenied.status), role=$($normalRoleDenied.status)"

    $sourceAdmin = Get-Content -Raw -Encoding UTF8 (Join-Path $RepoRoot 'frontend/src/features/system-admin/systemAdmin.ts')
    $sourceApi = Get-Content -Raw -Encoding UTF8 (Join-Path $RepoRoot 'frontend/src/api/liveData.ts')
    $deployedAssetText = Read-DeployedAssetText
    Add-Check $checks 'source' 'source contains R90 org member markers' ($sourceAdmin -match 'orgMemberDeliveryR90' -and $sourceAdmin -match 'r90BindAccount' -and $sourceApi -match 'bindSystemMemberAccount') 'source markers present'
    Add-Check $checks 'deployed' 'deployed asset contains R90 org member markers' ($deployedAssetText -match 'orgMemberDeliveryR90' -and $deployedAssetText -match 'r90BindAccount') 'deployed markers present'

    $debugPort = Get-FreeTcpPort
    $chrome = Find-Chrome
    $chromeProfile = Join-Path $BrowserDir "chrome-profile-$stamp"
    New-Item -ItemType Directory -Force -Path $chromeProfile | Out-Null
    $chromeArgs = @('--headless=new', '--disable-gpu', '--disable-extensions', '--no-sandbox', '--no-first-run', '--no-default-browser-check', '--remote-allow-origins=*', "--remote-debugging-port=$debugPort", "--user-data-dir=$chromeProfile", 'about:blank')
    $chromeProcess = Start-Process -FilePath $chrome -ArgumentList $chromeArgs -PassThru -WindowStyle Hidden
    $ready = $false
    for ($i = 0; $i -lt 50; $i++) {
        try { Invoke-RestMethod -Uri "http://127.0.0.1:$debugPort/json/version" -TimeoutSec 2 | Out-Null; $ready = $true; break } catch { Start-Sleep -Milliseconds 200 }
    }
    if (-not $ready) { throw 'Chrome DevTools endpoint did not become ready.' }
    $browserOut = Join-Path $BrowserDir 'org-member-role-binding-browser-audit.json'
    $env:R90_BASE_URL = $BaseUrl
    $env:R90_CDP_PORT = [string]$debugPort
    $env:R90_BROWSER_OUT = $browserOut
    $env:R90_SYSTEM_ID = $systemId
    $env:R90_ADMIN_ACCESS_TOKEN = [string]$adminReg.accessToken
    $env:R90_ADMIN_REFRESH_TOKEN = [string]$adminReg.refreshToken
    $env:R90_ADMIN_ACCOUNT_ID = [string]$adminReg.accountId
    $env:R90_NORMAL_ACCESS_TOKEN = [string]$normalReg.accessToken
    $env:R90_NORMAL_REFRESH_TOKEN = [string]$normalReg.refreshToken
    $env:R90_NORMAL_ACCOUNT_ID = [string]$normalReg.accountId
    $nodeExe = if (Test-Path 'D:\dev\nodejs24\node.exe') { 'D:\dev\nodejs24\node.exe' } else { 'node' }
    $nodeOutput = & $nodeExe $BrowserScriptPath 2>&1
    if ($LASTEXITCODE -ne 0) { throw "R90 browser audit failed: $nodeOutput" }
    $browser = Get-Content -Raw -LiteralPath $browserOut | ConvertFrom-Json
    if ($browser.error) { throw "R90 browser audit failed: $($browser.error)" }
    $browserResults = @($browser.results)
    $adminOrg = @($browserResults | Where-Object { $_.kind -eq 'admin-org-desktop' }) | Select-Object -First 1
    $normalDenied = @($browserResults | Where-Object { $_.kind -eq 'normal-admin-denied' }) | Select-Object -First 1
    $overflowCount = @($browserResults | Where-Object { $_.overflowX -gt 0 }).Count
    $blockerCount = @($browserResults | Where-Object { $_.blockerText -eq $true }).Count
    Add-Check $checks 'browser' 'admin browser exposes R90 org member delivery markers' ($adminOrg.orgPanel -and $adminOrg.memberRows -ge 1 -and $adminOrg.selectedBinding -eq 'BOUND' -and -not [string]::IsNullOrWhiteSpace($adminOrg.selectedRoles)) "members=$($adminOrg.memberRows) binding=$($adminOrg.selectedBinding) roles=$($adminOrg.selectedRoles)"
    Add-Check $checks 'browser' 'normal browser is denied from system admin surface' (-not $normalDenied.orgPanel -and $normalDenied.deniedCopy) "orgPanel=$($normalDenied.orgPanel) deniedCopy=$($normalDenied.deniedCopy)"
    Add-Check $checks 'browser' 'R90 browser audit has no overflow or blocker text' ($overflowCount -eq 0 -and $blockerCount -eq 0) "overflow=$overflowCount blockers=$blockerCount results=$($browserResults.Count)"

    $state = Get-Content -Raw -Encoding UTF8 (Join-Path $RepoRoot '.cursor/session/state.json') | ConvertFrom-Json
    Add-Check $checks 'signoff-boundary' 'R90 does not close user signoff' ($state.gates.user_script_passed -eq $false) "user_script_passed=$($state.gates.user_script_passed)"
} catch {
    $status = 'FAIL'
    $errorMessage = $_.Exception.Message
    Add-Check $checks 'fatal' 'R90 script completed without fatal error' $false $errorMessage
} finally {
    if ($chromeProcess -and -not $chromeProcess.HasExited) { Stop-Process -Id $chromeProcess.Id -Force -ErrorAction SilentlyContinue }
    if ($chromeProfile -and (Test-Path -LiteralPath $chromeProfile)) { Remove-Item -LiteralPath $chromeProfile -Recurse -Force -ErrorAction SilentlyContinue }
}

$failed = @($checks | Where-Object { -not $_.passed })
if ($failed.Count -gt 0) { $status = 'FAIL' }
$result = [ordered]@{
    status = $status
    task = 'REC-P0-090'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    userSignoff = $false
    systemId = $systemId
    deptId = [string]$dept.deptId
    memberId = [string]$member.systemMemberId
    roleId = [string]$role.roleId
    roleCode = [string]$role.roleCode
    bindingId = [string]$binding.bindingId
    normalLoginName = $normalLoginName
    checks = $checks
    browserAudit = 'docs/evidence/recovery/screenshots/r90-org-member-role-binding-first-use/org-member-role-binding-browser-audit.json'
    error = $errorMessage
}
$result | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $ResultPath -Encoding UTF8
$summary = @"
# R90 System Org Member Role Binding First-Use

Status: $status

- Base URL: $BaseUrl
- System: $systemId
- Department: $($dept.deptId)
- Member: $($member.systemMemberId)
- Role: $($role.roleId) / $($role.roleCode)
- Binding: $($binding.bindingId)
- Browser audit: docs/evidence/recovery/screenshots/r90-org-member-role-binding-first-use/org-member-role-binding-browser-audit.json
- User signoff: false

Failed checks: $($failed.Count)
"@
$summary | Set-Content -LiteralPath $SummaryPath -Encoding UTF8
Write-Host "R90 status=$status; result=$ResultPath"
if ($status -ne 'PASS' -and -not $NoFailExit) { exit 1 }