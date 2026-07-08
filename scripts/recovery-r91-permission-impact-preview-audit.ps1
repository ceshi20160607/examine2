param(
    [string]$BaseUrl = 'http://127.0.0.1:18131',
    [switch]$NoFailExit
)

$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$EvidenceDir = Join-Path $RepoRoot 'docs/evidence/recovery'
$BrowserDir = Join-Path $EvidenceDir 'screenshots/r91-permission-impact-preview-audit'
$ResultPath = Join-Path $EvidenceDir 'r91-permission-impact-preview-audit-result.json'
$SummaryPath = Join-Path $EvidenceDir 'r91-permission-impact-preview-audit-2026-07-07.md'
$BrowserScriptPath = Join-Path $RepoRoot 'scripts/recovery-r91-browser-audit.js'
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
            try { $body = ([System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())).ReadToEnd() } catch {}
        }
        throw "API failed: $Method $Path -> HTTP $status $body"
    }
    if ($response.code -ne 'SUCCESS') { throw "API failed: $Method $Path -> $($response | ConvertTo-Json -Depth 30 -Compress)" }
    return $response.data
}

function Invoke-ExpectedDenied {
    param([string]$Method, [string]$Path, [object]$Body = $null, [hashtable]$Headers = @{})
    try { $null = Invoke-Api -Method $Method -Path $Path -Body $Body -Headers $Headers } catch {
        if ($_.Exception.Message -match 'HTTP 401|HTTP 403|PERMISSION_DENIED|没有系统后台管理权限|权限|无权') {
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
        foreach ($match in $assetMatches) { $texts.Add((Invoke-WebRequest -Uri "$BaseUrl$($match.Groups['src'].Value)" -UseBasicParsing -TimeoutSec 30).Content) | Out-Null }
        return ($texts -join "`n")
    } catch { return '' }
}

function Get-FreeTcpPort {
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
    $listener.Start(); $port = $listener.LocalEndpoint.Port; $listener.Stop(); return $port
}

function Find-Chrome {
    $paths = @("$env:ProgramFiles\Google\Chrome\Application\chrome.exe", "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe", "$env:LocalAppData\Google\Chrome\Application\chrome.exe", "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe", "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe")
    foreach ($candidate in $paths) { if ($candidate -and (Test-Path -LiteralPath $candidate)) { return $candidate } }
    throw 'Chrome or Edge executable was not found.'
}

function New-FieldBody {
    param([string]$Code, [string]$Name, [string]$Type = 'TEXT', [bool]$Required = $false)
    return @{ fieldCode = $Code; name = $Name; fieldType = $Type; storageType = if ($Type -eq 'NUMBER') { 'DECIMAL' } else { 'VARCHAR' }; required = $Required; sortable = $true; filterOperators = @('EQ', 'LIKE'); maskRule = 'NONE'; importExportRule = @{ importable = $true; exportable = $true; requiredOnImport = $Required; duplicateKey = 'none'; desensitizeMode = 'PERMISSION' } }
}

$checks = [System.Collections.Generic.List[object]]::new()
$stamp = Get-Date -Format 'yyyyMMddHHmmss'
$password = 'Aa123456!'
$adminLoginName = "r91_admin_$stamp"
$normalLoginName = "r91_normal_$stamp"
$adminSystemCode = "r91_sys_$stamp"
$normalSystemCode = "r91_own_$stamp"
$chromeProcess = $null
$chromeProfile = $null
$status = 'PASS'
$errorMessage = $null
$systemId = ''
$moduleId = ''
$roleId = ''
$memberId = ''
$recordId = ''

try {    $health = Invoke-Api -Method Get -Path '/api/v1/health'
    Add-Check $checks 'release' 'release health is reachable' ($health.status -eq 'UP') "status=$($health.status)"

    $adminReg = Invoke-Api -Method Post -Path '/api/v1/auth/register-with-system' -Body @{ accountName = $adminLoginName; mobile = "139$($stamp.Substring($stamp.Length - 8))"; email = "$adminLoginName@example.test"; password = $password; systemName = "R91 Permission System $stamp"; systemCode = $adminSystemCode; tenantMode = 1; templateCode = 'blank' }
    $systemId = [string]$adminReg.systemId
    $adminHeaders = @{ Authorization = "Bearer $($adminReg.accessToken)" }
    $adminSwitch = Invoke-Api -Method Post -Path '/api/v1/platform/system-switch' -Headers $adminHeaders -Body @{ systemId = $systemId; tenantId = $null; reason = 'R91 permission impact setup' }
    Add-Check $checks 'setup' 'registered admin owns new system' (@($adminSwitch.effectiveRoleIds) -contains 'SYSTEM_SUPER_ADMIN') "system=$systemId"

    $normalReg = Invoke-Api -Method Post -Path '/api/v1/auth/register-with-system' -Body @{ accountName = $normalLoginName; mobile = "138$($stamp.Substring($stamp.Length - 8))"; email = "$normalLoginName@example.test"; password = $password; systemName = "R91 Normal Owned $stamp"; systemCode = $normalSystemCode; tenantMode = 1; templateCode = 'blank' }
    $normalHeaders = @{ Authorization = "Bearer $($normalReg.accessToken)" }

    $dept = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/org/departments" -Headers $adminHeaders -Body @{ parentId = '0'; deptCode = "r91_dept_$stamp"; deptName = "R91 Dept $stamp"; sortOrder = 10; status = 1 }
    $role = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/roles" -Headers $adminHeaders -Body @{ roleName = "R91 Limited Member $stamp"; roleCode = "R91_LIMITED_$stamp"; roleType = 'CUSTOM'; status = 1; description = 'R91 permission impact preview role' }
    $roleId = [string]$role.roleId
    $member = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/members" -Headers $adminHeaders -Body @{ deptId = [string]$dept.deptId; memberName = "R91 Normal Member $stamp"; employeeNo = "R91-$stamp"; mobile = "137$($stamp.Substring($stamp.Length - 8))"; email = "member-r91-$stamp@example.test"; status = 1; roleIds = @() }
    $memberId = [string]$member.systemMemberId
    $binding = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/members/$memberId/bind-account" -Headers $adminHeaders -Body @{ loginName = $normalLoginName; bindMode = 'BIND' }
    $assigned = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/roles/$roleId/assign-members" -Headers $adminHeaders -Body @{ systemMemberIds = @($memberId); accountIds = @() }

    $rolesPage = Invoke-Api -Method Get -Path "/api/v1/systems/$systemId/roles?pageNo=1&pageSize=100" -Headers $adminHeaders
    $superRole = @($rolesPage.records | Where-Object { $_.roleCode -eq 'SYSTEM_SUPER_ADMIN' }) | Select-Object -First 1
    $group = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/module-groups" -Headers $adminHeaders -Body @{ name = "R91 Module Group $stamp"; sort = 10; visibleRoleIds = @($roleId, [string]$superRole.roleId); publishStatus = 'DRAFT' }
    $module = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/modules" -Headers $adminHeaders -Body @{ groupId = [string]$group.groupId; moduleCode = "r91_case_$stamp"; name = "R91 Permission Case $stamp"; status = 1; description = 'R91 permission impact module' }
    $moduleId = [string]$module.moduleId
    $null = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/modules/$moduleId/fields" -Headers $adminHeaders -Body (New-FieldBody -Code 'publicName' -Name 'Public Name' -Type 'TEXT' -Required $true)
    $null = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/modules/$moduleId/fields" -Headers $adminHeaders -Body (New-FieldBody -Code 'secretNote' -Name 'Secret Note' -Type 'TEXT')
    $null = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/modules/$moduleId/fields" -Headers $adminHeaders -Body (New-FieldBody -Code 'amountValue' -Name 'Amount Value' -Type 'NUMBER')
    $null = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/modules/$moduleId/publish" -Headers $adminHeaders -Body @{ reason = 'recovery R91 module publish'; idempotencyKey = "module-publish-R91-$stamp" }
    $null = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/module-groups/$($group.groupId)/publish" -Headers $adminHeaders -Body @{ reason = 'recovery R91 group publish'; idempotencyKey = "group-publish-R91-$stamp" }

    $savedPermission = Invoke-Api -Method Put -Path "/api/v1/systems/$systemId/roles/$roleId/permissions" -Headers $adminHeaders -Body @{ menuPermissions = @{}; modulePermissions = @{ '*' = $true; $moduleId = $true }; actionPermissions = @{ 'record.read' = $true; 'record.create' = $false; 'record.edit' = $true; 'record.delete' = $false; 'record.submitApproval' = $true }; fieldPermissions = @{ "$moduleId.publicName" = 'READABLE'; "$moduleId.secretNote" = 'HIDDEN'; "$moduleId.amountValue" = 'READABLE' }; dataScopeRules = @(@{ type = 'SELF'; moduleId = $moduleId; expression = 'ownerMemberId == currentMember' }); denyPolicies = @() }
    $readbackPermission = Invoke-Api -Method Get -Path "/api/v1/systems/$systemId/roles/$roleId/permissions" -Headers $adminHeaders

    $batchPreview = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/permissions/effective/batch-preview" -Headers $adminHeaders -Body @{ systemMemberId = $memberId; roleIds = @($roleId); moduleId = $moduleId; recordId = $null; actionCodes = @('record.create', 'record.edit', 'record.delete', 'record.submitApproval') }
    $previewLogs = Invoke-Api -Method Get -Path "/api/v1/systems/$systemId/permissions/effective/preview-logs?roleId=$roleId&moduleId=$moduleId&pageSize=5" -Headers $adminHeaders
    $adminRecord = Invoke-Api -Method Post -Path "/api/v1/systems/$systemId/runtime/modules/$moduleId/records" -Headers $adminHeaders -Body @{ fieldValues = @{ publicName = "R91 Admin Record $stamp"; secretNote = 'hidden'; amountValue = 91 }; childRows = @{}; attachmentIds = @(); sourceType = 'WEB_FORM' }
    $recordId = [string]$adminRecord.recordId

    $normalSwitch = Invoke-Api -Method Post -Path '/api/v1/platform/system-switch' -Headers $normalHeaders -Body @{ systemId = $systemId; tenantId = $null; reason = 'R91 normal switch' }
    $normalBatchDenied = Invoke-ExpectedDenied -Method Post -Path "/api/v1/systems/$systemId/permissions/effective/batch-preview" -Headers $normalHeaders -Body @{ roleIds = @($roleId); moduleId = $moduleId; actionCodes = @('record.create') }
    $normalLogsDenied = Invoke-ExpectedDenied -Method Get -Path "/api/v1/systems/$systemId/permissions/effective/preview-logs?roleId=$roleId&moduleId=$moduleId&pageSize=5" -Headers $normalHeaders
    $normalCreateDenied = Invoke-ExpectedDenied -Method Post -Path "/api/v1/systems/$systemId/runtime/modules/$moduleId/records" -Headers $normalHeaders -Body @{ fieldValues = @{ publicName = "R91 Forbidden $stamp"; secretNote = 'forbidden'; amountValue = 1 }; childRows = @{}; attachmentIds = @(); sourceType = 'WEB_FORM' }

    $decisions = @($batchPreview.decisions)
    $createDenied = @($decisions | Where-Object { $_.actionCode -eq 'record.create' -and $_.allowed -eq $false }).Count -eq 1
    $editAllowed = @($decisions | Where-Object { $_.actionCode -eq 'record.edit' -and $_.allowed -eq $true }).Count -eq 1
    $deleteDenied = @($decisions | Where-Object { $_.actionCode -eq 'record.delete' -and $_.allowed -eq $false }).Count -eq 1
    $submitAllowed = @($decisions | Where-Object { $_.actionCode -eq 'record.submitApproval' -and $_.allowed -eq $true }).Count -eq 1
    $fieldNames = @($batchPreview.fieldMaskRules.PSObject.Properties.Name)
    $fieldMaskHit = ($fieldNames -contains "$moduleId.secretNote") -or ($fieldNames -contains 'secretNote')
    $scopeSelf = [string]$batchPreview.dataScopeExpression.type -eq 'SELF'

    Add-Check $checks 'setup' 'normal account is bound and assigned to R91 role' (($assigned.assignedCount -ge 1) -and ([string]$normalSwitch.systemMemberId -eq $memberId)) "member=$memberId role=$roleId binding=$($binding.bindingId)"
    Add-Check $checks 'api-save' 'role permission save/readback preserves action field and data-scope rules' (($readbackPermission.actionPermissions.'record.create' -eq $false) -and ($readbackPermission.actionPermissions.'record.edit' -eq $true) -and ([string]$readbackPermission.dataScopeRules[0].type -eq 'SELF')) "version=$($savedPermission.permissionVersion)"
    Add-Check $checks 'api-preview' 'batch preview returns mixed allow and deny decisions' ($createDenied -and $editAllowed -and $deleteDenied -and $submitAllowed) ($decisions | ConvertTo-Json -Depth 20 -Compress)
    Add-Check $checks 'api-preview' 'batch preview includes affected member count field mask and SELF data scope' (($batchPreview.affectedMemberCount -ge 1) -and $fieldMaskHit -and $scopeSelf) "affected=$($batchPreview.affectedMemberCount) masks=$($batchPreview.fieldMaskRules | ConvertTo-Json -Compress) scope=$($batchPreview.dataScopeExpression | ConvertTo-Json -Compress)"
    Add-Check $checks 'audit' 'preview audit rows are persisted and readable' (@($previewLogs).Count -ge 4) "rows=$(@($previewLogs).Count)"
    Add-Check $checks 'permission' 'normal member cannot use system-admin preview APIs and cannot create runtime record' (($normalBatchDenied.status -eq 'DENIED') -and ($normalLogsDenied.status -eq 'DENIED') -and ($normalCreateDenied.status -eq 'DENIED')) "batch=$($normalBatchDenied.status) logs=$($normalLogsDenied.status) create=$($normalCreateDenied.status)"
    $sourceAdmin = Get-Content -Raw -Encoding UTF8 (Join-Path $RepoRoot 'frontend/src/features/system-admin/systemAdmin.ts')
    $sourceApi = Get-Content -Raw -Encoding UTF8 (Join-Path $RepoRoot 'frontend/src/api/liveData.ts')
    $deployedAssetText = Read-DeployedAssetText
    $sourceMarkers = @('permissionImpactWorkbenchR91', 'r91BatchPreviewButton', 'r91PermissionDecision', 'r91PreviewAuditRow', 'previewSystemEffectivePermissions')
    $deployedMarkers = @('permissionImpactWorkbenchR91', 'r91BatchPreviewButton', 'r91PermissionDecision', 'r91PreviewAuditRow', 'batch-preview', 'preview-logs')
    $missingSourceMarkers = @($sourceMarkers | Where-Object { $sourceAdmin -notmatch [regex]::Escape($_) -and $sourceApi -notmatch [regex]::Escape($_) })
    $missingDeployedMarkers = @($deployedMarkers | Where-Object { $deployedAssetText -notmatch [regex]::Escape($_) })
    Add-Check $checks 'frontend-source' 'R91 frontend source markers exist' ($missingSourceMarkers.Count -eq 0) "missing=$($missingSourceMarkers -join ',')"
    Add-Check $checks 'deployed' 'deployed asset contains R91 permission impact markers' (($deployedAssetText.Length -gt 0) -and ($missingDeployedMarkers.Count -eq 0)) "assetLength=$($deployedAssetText.Length) missing=$($missingDeployedMarkers -join ',')"

    $debugPort = Get-FreeTcpPort
    $chrome = Find-Chrome
    $chromeProfile = Join-Path $BrowserDir "chrome-profile-$stamp"
    New-Item -ItemType Directory -Force -Path $chromeProfile | Out-Null
    $chromeArgs = @('--headless=new', '--disable-gpu', '--disable-extensions', '--no-sandbox', '--no-first-run', '--no-default-browser-check', '--remote-allow-origins=*', "--remote-debugging-port=$debugPort", "--user-data-dir=$chromeProfile", 'about:blank')
    $chromeProcess = Start-Process -FilePath $chrome -ArgumentList $chromeArgs -PassThru -WindowStyle Hidden
    $ready = $false
    for ($i = 0; $i -lt 50; $i++) { try { Invoke-RestMethod -Uri "http://127.0.0.1:$debugPort/json/version" -TimeoutSec 2 | Out-Null; $ready = $true; break } catch { Start-Sleep -Milliseconds 200 } }
    if (-not $ready) { throw 'Chrome DevTools endpoint did not become ready.' }
    $browserOut = Join-Path $BrowserDir 'permission-impact-preview-browser-audit.json'
    $env:R91_BASE_URL = $BaseUrl
    $env:R91_CDP_PORT = [string]$debugPort
    $env:R91_BROWSER_OUT = $browserOut
    $env:R91_SYSTEM_ID = $systemId
    $env:R91_ROLE_ID = $roleId
    $env:R91_MODULE_ID = $moduleId
    $env:R91_ADMIN_ACCESS_TOKEN = [string]$adminReg.accessToken
    $env:R91_ADMIN_REFRESH_TOKEN = [string]$adminReg.refreshToken
    $env:R91_ADMIN_ACCOUNT_ID = [string]$adminReg.accountId
    $env:R91_NORMAL_ACCESS_TOKEN = [string]$normalReg.accessToken
    $env:R91_NORMAL_REFRESH_TOKEN = [string]$normalReg.refreshToken
    $env:R91_NORMAL_ACCOUNT_ID = [string]$normalReg.accountId
    $nodeExe = if (Test-Path 'D:\dev\nodejs24\node.exe') { 'D:\dev\nodejs24\node.exe' } else { 'node' }
    $nodeOutput = & $nodeExe $BrowserScriptPath 2>&1
    if ($LASTEXITCODE -ne 0) { throw "R91 browser audit failed: $nodeOutput" }
    $browser = Get-Content -Raw -LiteralPath $browserOut | ConvertFrom-Json
    if ($browser.error) { throw "R91 browser audit failed: $($browser.error)" }
    $browserResults = @($browser.results)
    $adminDesktop = @($browserResults | Where-Object { $_.kind -eq 'admin-role-desktop' }) | Select-Object -First 1
    $adminMobile = @($browserResults | Where-Object { $_.kind -eq 'admin-role-mobile' }) | Select-Object -First 1
    $normalDenied = @($browserResults | Where-Object { $_.kind -eq 'normal-admin-denied' }) | Select-Object -First 1
    $browserOverflowCount = @($browserResults | Where-Object { $_.overflowX -gt 0 }).Count
    $browserBlockerCount = @($browserResults | Where-Object { $_.blockerText -eq $true }).Count
    Add-Check $checks 'browser' 'admin browser exposes R91 permission impact preview decisions and audit rows' ($adminDesktop.workbench -and $adminDesktop.decisionCount -ge 4 -and $adminDesktop.deniedCreate -and $adminDesktop.allowedEdit -and $adminDesktop.deniedDelete -and $adminDesktop.allowedSubmit -and [int]$adminDesktop.affectedMembers -ge 1 -and $adminDesktop.auditCount -ge 1) ($adminDesktop | ConvertTo-Json -Depth 10 -Compress)
    Add-Check $checks 'browser' 'mobile admin browser keeps R91 workbench contained' ($adminMobile.workbench -and $adminMobile.overflowX -eq 0) ($adminMobile | ConvertTo-Json -Depth 10 -Compress)
    Add-Check $checks 'browser' 'normal browser is denied from system admin permission workbench' (-not $normalDenied.workbench -and $normalDenied.deniedCopy) ($normalDenied | ConvertTo-Json -Depth 10 -Compress)
    Add-Check $checks 'browser' 'R91 browser audit has no overflow or blocker text' (($browserOverflowCount -eq 0) -and ($browserBlockerCount -eq 0)) "overflow=$browserOverflowCount blockers=$browserBlockerCount results=$($browserResults.Count)"

    $state = Get-Content -Raw -Encoding UTF8 (Join-Path $RepoRoot '.cursor/session/state.json') | ConvertFrom-Json
    Add-Check $checks 'signoff-boundary' 'R91 does not close user signoff' ($state.gates.user_script_passed -eq $false) "user_script_passed=$($state.gates.user_script_passed)"
} catch {
    $status = 'FAIL'
    $errorMessage = $_.Exception.Message
    Add-Check $checks 'fatal' 'R91 script completed without fatal error' $false $errorMessage
} finally {
    if ($chromeProcess -and -not $chromeProcess.HasExited) { Stop-Process -Id $chromeProcess.Id -Force -ErrorAction SilentlyContinue }
    if ($chromeProfile -and (Test-Path -LiteralPath $chromeProfile)) { Remove-Item -LiteralPath $chromeProfile -Recurse -Force -ErrorAction SilentlyContinue }
}

$failed = @($checks | Where-Object { -not $_.passed })
if ($failed.Count -gt 0) { $status = 'FAIL' }
$result = [ordered]@{ status = $status; task = 'REC-P0-091'; generatedAt = (Get-Date).ToString('o'); baseUrl = $BaseUrl; userSignoff = $false; systemId = $systemId; moduleId = $moduleId; roleId = $roleId; memberId = $memberId; recordId = $recordId; normalLoginName = $normalLoginName; checks = $checks; browserAudit = 'docs/evidence/recovery/screenshots/r91-permission-impact-preview-audit/permission-impact-preview-browser-audit.json'; error = $errorMessage }
$result | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $ResultPath -Encoding UTF8
$summary = @()
$summary += '# R91 Permission Impact Preview Audit Evidence'
$summary += ''
$summary += ('- Status: `{0}`' -f $status)
$summary += ('- System: `{0}`' -f $systemId)
$summary += ('- Role: `{0}`' -f $roleId)
$summary += ('- Module: `{0}`' -f $moduleId)
$summary += ('- Member: `{0}`' -f $memberId)
$summary += '- Browser audit: `docs/evidence/recovery/screenshots/r91-permission-impact-preview-audit/permission-impact-preview-browser-audit.json`'
$summary += '- Browser DOM remains visual evidence only; API/readback assertions prove permission decisions, audit rows, runtime denial, and signoff separation.'
$summary += '- User signoff remains `false`.'
$summary -join "`r`n" | Set-Content -Encoding UTF8 -LiteralPath $SummaryPath
Write-Host ("R91 status={0}; result={1}" -f $status, $ResultPath)
if (($status -ne 'PASS') -and (-not $NoFailExit)) { exit 1 }
