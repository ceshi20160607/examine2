param(
    [string]$BaseUrl = 'http://127.0.0.1:18131',
    [switch]$NoFailExit
)

$ErrorActionPreference = 'Stop'

function Add-Check {
    param(
        [System.Collections.Generic.List[object]]$Checks,
        [string]$Area,
        [string]$Name,
        [bool]$Passed,
        [string]$Detail
    )
    $Checks.Add([ordered]@{
        area = $Area
        name = $Name
        passed = $Passed
        detail = $Detail
    }) | Out-Null
    if (-not $Passed) {
        throw "$Area/$Name failed: $Detail"
    }
}

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
            $response = Invoke-RestMethod -Method $Method -Uri "$BaseUrl$Path" -Headers $Headers -TimeoutSec 30
        } else {
            $response = Invoke-RestMethod -Method $Method -Uri "$BaseUrl$Path" -Headers $Headers -ContentType 'application/json; charset=utf-8' -Body $jsonBody -TimeoutSec 30
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

function Invoke-ChildScript {
    param(
        [string]$Name,
        [string]$ScriptPath,
        [string[]]$Arguments
    )
    $logFile = Join-Path $script:EvidenceDir "$Name.log"
    $allArgs = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $ScriptPath) + $Arguments
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & powershell @allArgs 2>&1
        $exitCode = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    $output | Set-Content -LiteralPath $logFile -Encoding UTF8
    if ($exitCode -ne 0) {
        throw "Child script failed: $Name exitCode=$exitCode log=$logFile"
    }
    return [ordered]@{
        name = $Name
        exitCode = $exitCode
        logFile = $logFile
    }
}

function Read-JsonFile {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Expected result file was not found: $Path"
    }
    return (Get-Content -Raw -Encoding UTF8 -LiteralPath $Path | ConvertFrom-Json)
}

function Has-All {
    param(
        [object[]]$Actual,
        [string[]]$Expected
    )
    foreach ($item in $Expected) {
        if (-not ($Actual -contains $item)) {
            return $false
        }
    }
    return $true
}

trap {
    if ($script:FailureResultFile) {
        $failure = [ordered]@{
            status = 'FAIL'
            productStatus = 'PARTIAL_ENGINEERING_EVIDENCE_ONLY'
            generatedAt = (Get-Date).ToString('o')
            baseUrl = $BaseUrl
            error = $_.Exception.Message
            checks = @($script:Checks.ToArray())
        }
        $failure | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $script:FailureResultFile -Encoding UTF8
    }
    if ($NoFailExit) {
        Write-Output ($failure | ConvertTo-Json -Depth 30)
        exit 0
    }
    throw
}

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$script:Checks = New-Object System.Collections.Generic.List[object]
$script:EvidenceDir = Join-Path $RepoRoot 'docs\evidence\recovery\screenshots\r53-frc2-no-code-configuration'
$ResultFile = Join-Path $RepoRoot 'docs\evidence\recovery\r53-frc2-no-code-configuration-coherence-result.json'
$SummaryFile = Join-Path $RepoRoot 'docs\evidence\recovery\r53-frc2-no-code-configuration-coherence-2026-07-01.md'
$BrowserAuditFile = Join-Path $script:EvidenceDir 'no-code-configuration-browser-audit.json'
$script:FailureResultFile = $ResultFile
New-Item -ItemType Directory -Force -Path $script:EvidenceDir | Out-Null
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $ResultFile) | Out-Null

$Health = Invoke-Api -Method 'Get' -Path '/api/v1/health'
Add-Check $script:Checks 'release' 'health reports database/schema/redis UP' `
    ($Health.status -eq 'UP' -and $Health.database -eq 'UP' -and $Health.schema -eq 'UP' -and $Health.redis -eq 'UP') `
    ($Health | ConvertTo-Json -Depth 12 -Compress)

$childResults = @()
$childResults += Invoke-ChildScript 'verify-release' (Join-Path $PSScriptRoot 'verify-release.ps1') @('-BaseUrl', $BaseUrl, '-CheckDeployedFrontend')
$childResults += Invoke-ChildScript 'final-usability-static-audit' (Join-Path $PSScriptRoot 'final-usability-static-audit.ps1') @('-NoFailExit')

$StaticAudit = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\final-usability-static-audit-result.json')
Add-Check $script:Checks 'frontend-static' 'no blocker or warning copy/placeholder findings' `
    ($StaticAudit.status -eq 'PASS' -and [int]$StaticAudit.blockerCount -eq 0 -and [int]$StaticAudit.warningCount -eq 0) `
    "blockers=$($StaticAudit.blockerCount), warnings=$($StaticAudit.warningCount)"

$childResults += Invoke-ChildScript 'recovery-r38-module-lifecycle-admin-table' `
    (Join-Path $PSScriptRoot 'recovery-r38-module-lifecycle-admin-table-smoke.ps1') @('-BaseUrl', $BaseUrl)
$R38 = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\recovery\r38-module-lifecycle-admin-table-result.json')
Add-Check $script:Checks 'module-config' 'R38 fresh module lifecycle and admin table parity passed' `
    ($R38.status -eq 'PASS' -and [int]$R38.schemaColumnCount -ge 3 -and [int]$R38.schemaFilterCount -ge 2 -and [int]$R38.schemaSorterCount -ge 1 -and [int]$R38.importExportMappingCount -ge 3 -and $R38.publishCheckPassed -eq $true -and [string]$R38.publishedVersion -match '^MODULE_v' -and [string]$R38.rollbackResult -ne '' -and [int]$R38.forbiddenCreateStatus -eq 403 -and [int]$R38.forbiddenAdminStatus -eq 403 -and [int]$R38.browserOverflowCount -eq 0 -and [int]$R38.browserBlockerCount -eq 0) `
    "columns=$($R38.schemaColumnCount), filters=$($R38.schemaFilterCount), sorters=$($R38.schemaSorterCount), mappings=$($R38.importExportMappingCount), forbidden=$($R38.forbiddenCreateStatus)/$($R38.forbiddenAdminStatus), browser=$($R38.browserResultCount)"

$childResults += Invoke-ChildScript 'recovery-r44-no-code-permission-preview' `
    (Join-Path $PSScriptRoot 'recovery-r44-no-code-permission-preview-smoke.ps1') @('-BaseUrl', $BaseUrl)
$R44 = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\recovery\r44-no-code-permission-preview-result.json')
Add-Check $script:Checks 'permission-preview' 'R44 fresh no-code permission preview agrees with runtime permission' `
    ($R44.status -eq 'PASS' -and -not [string]::IsNullOrWhiteSpace([string]$R44.runtimeRoleId) -and [int]$R44.dictItemCount -ge 2 -and [int]$R44.fieldCount -ge 3 -and $R44.previewAllowed -eq $false -and [string]$R44.previewHiddenSecret -eq 'HIDDEN' -and [int]$R44.normalSchemaColumnCount -gt 0 -and $R44.normalSecretColumnLeaked -eq $false -and $R44.normalCreateDisabled -eq $true -and [int]$R44.forbiddenCreateStatus -eq 403 -and [int]$R44.forbiddenAdminStatus -eq 403 -and [int]$R44.browserOverflowCount -eq 0 -and [int]$R44.browserBlockerCount -eq 0) `
    "role=$($R44.runtimeRoleId), previewAllowed=$($R44.previewAllowed), secretLeaked=$($R44.normalSecretColumnLeaked), createDisabled=$($R44.normalCreateDisabled), browser=$($R44.browserResultCount)"

$childResults += Invoke-ChildScript 'recovery-r45-field-dict-menu' `
    (Join-Path $PSScriptRoot 'recovery-r45-field-dict-menu-smoke.ps1') @('-BaseUrl', $BaseUrl)
$R45 = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\recovery\r45-field-dict-menu-result.json')
$ExpectedTypes = @('LONG_TEXT','NUMBER','DATE','DATETIME','SELECT','MULTI_SELECT','USER','DEPARTMENT','ATTACHMENT','IMAGE','RELATION','CHILD_TABLE')
$ActualTypes = @($R45.fieldTypes | ForEach-Object { [string]$_ })
$ActiveOptionCodes = @($R45.activeSchemaOptionCodes | ForEach-Object { [string]$_ })
Add-Check $script:Checks 'field-dict-menu' 'R45 fresh field type dictionary and menu configuration passed' `
    ($R45.status -eq 'PASS' -and -not [string]::IsNullOrWhiteSpace([string]$R45.runtimeRoleId) -and [int]$R45.fieldCount -ge 12 -and (Has-All -Actual $ActualTypes -Expected $ExpectedTypes) -and [int]$R45.dictFieldReferenceCount -ge 2 -and [int]$R45.dictDisabledItemCount -eq 1 -and ($ActiveOptionCodes -contains 'ACTIVE') -and ($ActiveOptionCodes -contains 'PENDING') -and -not ($ActiveOptionCodes -contains 'DISABLED_OLD') -and [int]$R45.normalSchemaColumnCount -gt 0 -and [int]$R45.forbiddenAdminStatus -eq 403 -and [int]$R45.browserOverflowCount -eq 0 -and [int]$R45.browserBlockerCount -eq 0) `
    "fields=$($R45.fieldCount), types=$($ActualTypes -join ','), activeOptions=$($ActiveOptionCodes -join ','), normalSchema=$($R45.normalSchemaColumnCount), browser=$($R45.browserResultCount)"

$browserAudit = [ordered]@{
    status = 'PASS'
    generatedAt = (Get-Date).ToString('o')
    source = 'R53 aggregation of fresh R38/R44/R45 deployed browser audits'
    r38 = @{
        resultFile = 'docs/evidence/recovery/r38-module-lifecycle-admin-table-result.json'
        browserResultCount = $R38.browserResultCount
        browserOverflowCount = $R38.browserOverflowCount
        browserBlockerCount = $R38.browserBlockerCount
        deployedScripts = @($R38.deployedScripts)
    }
    r44 = @{
        resultFile = 'docs/evidence/recovery/r44-no-code-permission-preview-result.json'
        browserAuditPath = $R44.browserAuditPath
        browserResultCount = $R44.browserResultCount
        browserOverflowCount = $R44.browserOverflowCount
        browserBlockerCount = $R44.browserBlockerCount
    }
    r45 = @{
        resultFile = 'docs/evidence/recovery/r45-field-dict-menu-result.json'
        browserAuditPath = $R45.browserAuditPath
        browserResultCount = $R45.browserResultCount
        browserOverflowCount = $R45.browserOverflowCount
        browserBlockerCount = $R45.browserBlockerCount
    }
}
$browserAudit | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $BrowserAuditFile -Encoding UTF8

$result = [ordered]@{
    status = 'PASS'
    productStatus = 'PARTIAL_ENGINEERING_EVIDENCE_ONLY'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    requirementRows = @('REQ-4.3', 'REQ-5.3', 'REQ-5.3.1', 'REQ-5.4', 'REQ-5.5', 'REQ-5.6', 'REQ-5.7', 'REQ-5.9', 'REQ-5.10', 'REQ-6.7', 'REQ-6.10')
    journeyRows = @('J2', 'J3', 'J5', 'J8', 'J9', 'J11')
    userSignoff = $false
    checks = @($script:Checks.ToArray())
    childResults = $childResults
    staticAudit = @{
        status = $StaticAudit.status
        blockerCount = $StaticAudit.blockerCount
        warningCount = $StaticAudit.warningCount
    }
    browserAuditFile = 'docs/evidence/recovery/screenshots/r53-frc2-no-code-configuration/no-code-configuration-browser-audit.json'
    moduleConfig = @{
        systemId = $R38.systemId
        moduleId = $R38.moduleId
        schemaColumnCount = $R38.schemaColumnCount
        schemaFilterCount = $R38.schemaFilterCount
        schemaSorterCount = $R38.schemaSorterCount
        importExportMappingCount = $R38.importExportMappingCount
        publishCheckPassed = $R38.publishCheckPassed
        rollbackResult = $R38.rollbackResult
    }
    permissionPreview = @{
        systemId = $R44.systemId
        moduleId = $R44.moduleId
        runtimeRoleId = $R44.runtimeRoleId
        previewAllowed = $R44.previewAllowed
        previewHiddenSecret = $R44.previewHiddenSecret
        normalSchemaColumnCount = $R44.normalSchemaColumnCount
        normalSecretColumnLeaked = $R44.normalSecretColumnLeaked
        normalCreateDisabled = $R44.normalCreateDisabled
    }
    fieldDictMenu = @{
        systemId = $R45.systemId
        moduleId = $R45.moduleId
        runtimeRoleId = $R45.runtimeRoleId
        dictPublishedVersion = $R45.dictPublishedVersion
        dictFieldReferenceCount = $R45.dictFieldReferenceCount
        dictDisabledItemCount = $R45.dictDisabledItemCount
        activeSchemaOptionCodes = @($R45.activeSchemaOptionCodes)
        fieldCount = $R45.fieldCount
        fieldTypes = @($R45.fieldTypes)
        normalSchemaColumnCount = $R45.normalSchemaColumnCount
    }
}
$result | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $ResultFile -Encoding UTF8

$summary = @"
# REC-P0-053 / R53 FRC-2 No-Code Configuration Coherence Fresh Closure

Status: PASS as deployed engineering evidence only.

- Base URL: $BaseUrl
- Requirement rows: REQ-4.3, REQ-5.3, REQ-5.3.1, REQ-5.4, REQ-5.5, REQ-5.6, REQ-5.7, REQ-5.9, REQ-5.10, REQ-6.7, REQ-6.10
- Fresh module lifecycle/admin table: R38 PASS, columns=$($R38.schemaColumnCount), filters=$($R38.schemaFilterCount), sorters=$($R38.schemaSorterCount), mappings=$($R38.importExportMappingCount), browserResultCount=$($R38.browserResultCount), overflow=$($R38.browserOverflowCount), blockers=$($R38.browserBlockerCount)
- Fresh permission preview/runtime agreement: R44 PASS, runtimeRoleId=$($R44.runtimeRoleId), previewAllowed=$($R44.previewAllowed), normalSecretColumnLeaked=$($R44.normalSecretColumnLeaked), normalCreateDisabled=$($R44.normalCreateDisabled), browserResultCount=$($R44.browserResultCount), overflow=$($R44.browserOverflowCount), blockers=$($R44.browserBlockerCount)
- Fresh field/dictionary/menu configuration: R45 PASS, fieldCount=$($R45.fieldCount), dictDisabledItemCount=$($R45.dictDisabledItemCount), activeOptions=$($ActiveOptionCodes -join ', '), normalSchemaColumnCount=$($R45.normalSchemaColumnCount), browserResultCount=$($R45.browserResultCount), overflow=$($R45.browserOverflowCount), blockers=$($R45.browserBlockerCount)
- Static usability audit: status=$($StaticAudit.status), blockers=$($StaticAudit.blockerCount), warnings=$($StaticAudit.warningCount)
- Browser aggregation: `docs/evidence/recovery/screenshots/r53-frc2-no-code-configuration/no-code-configuration-browser-audit.json`

This does not close final product acceptance. Full requirement coverage and user signoff remain open.
"@
$summary | Set-Content -LiteralPath $SummaryFile -Encoding UTF8

$result | ConvertTo-Json -Depth 30
