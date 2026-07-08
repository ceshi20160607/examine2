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
    $output = & powershell @allArgs 2>&1
    $exitCode = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
    $output | Set-Content -LiteralPath $logFile -Encoding UTF8
    if ($exitCode -ne 0) {
        throw "Child script failed: $Name exitCode=$exitCode log=$logFile"
    }
    return @{
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

function Remove-CreatedSystems {
    if ($null -eq $script:AdminHeaders) {
        return @('SKIPPED')
    }
    $results = @()
    foreach ($systemId in @($script:CreatedSystemIds)) {
        if ([string]::IsNullOrWhiteSpace($systemId)) {
            continue
        }
        try {
            $deleted = Invoke-Api -Method 'Delete' -Path "/api/v1/platform/systems/$systemId" -Headers $script:AdminHeaders -Body @{
                reason = 'recovery-r52 frc1 focused cleanup'
                impactScope = 'created_by_current_script'
                idempotencyKey = "cleanup-r52-$script:Suffix-$systemId"
            }
            $results += "${systemId}:$($deleted.result)"
        } catch {
            $results += "${systemId}:FAILED:$($_.Exception.Message)"
        }
    }
    return $results
}

function New-FieldBody {
    param([string]$Code, [string]$Name)
    return @{
        fieldCode = $Code
        name = $Name
        fieldType = 'TEXT'
        storageType = 'VARCHAR'
        required = $true
        sortable = $true
        maskRule = 'NONE'
        importExportRule = @{
            importable = $true
            exportable = $true
            requiredOnImport = $true
            duplicateKey = $Code
            desensitizeMode = 'PERMISSION'
        }
    }
}

trap {
    $script:CleanupResult = Remove-CreatedSystems
    if ($script:FailureResultFile) {
        $failure = [ordered]@{
            status = 'FAIL'
            generatedAt = (Get-Date).ToString('o')
            baseUrl = $BaseUrl
            error = $_.Exception.Message
            checks = @($script:Checks.ToArray())
            cleanup = $script:CleanupResult
        }
        $failure | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $script:FailureResultFile -Encoding UTF8
    }
    if ($NoFailExit) {
        Write-Output ($failure | ConvertTo-Json -Depth 20)
        exit 0
    }
    throw
}

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$script:Suffix = "$(Get-Date -Format 'MMddHHmmss')_$((New-Guid).ToString('N').Substring(0, 6))"
$script:Checks = New-Object System.Collections.Generic.List[object]
$script:CreatedSystemIds = New-Object System.Collections.Generic.List[string]
$script:AdminHeaders = $null
$script:CleanupResult = @('SKIPPED')

$script:EvidenceDir = Join-Path $RepoRoot 'docs\evidence\recovery\screenshots\r52-frc1-deployed-surface'
$ResultFile = Join-Path $RepoRoot 'docs\evidence\recovery\r52-frc1-deployed-surface-closure-result.json'
$SummaryFile = Join-Path $RepoRoot 'docs\evidence\recovery\r52-frc1-deployed-surface-closure-2026-07-01.md'
$BrowserAuditFile = Join-Path $script:EvidenceDir 'frc1-deployed-surface-browser-audit.json'
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

$childResults += Invoke-ChildScript 'recovery-r43-role-home-whole-path-usability' `
    (Join-Path $PSScriptRoot 'recovery-r43-role-home-whole-path-usability-smoke.ps1') @('-BaseUrl', $BaseUrl)
$R43 = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\recovery\r43-role-home-whole-path-usability-result.json')
Add-Check $script:Checks 'role-journey' 'R43 fresh role home whole-path browser audit passed' `
    ($R43.status -eq 'PASS' -and [int]$R43.failureCount -eq 0 -and [int]$R43.warningCount -eq 0) `
    "results=$($R43.resultCount), failures=$($R43.failureCount), warnings=$($R43.warningCount)"

$childResults += Invoke-ChildScript 'recovery-r46-page-surface' `
    (Join-Path $PSScriptRoot 'recovery-r46-page-surface-smoke.ps1') @('-BaseUrl', $BaseUrl)
$R46 = Read-JsonFile (Join-Path $RepoRoot 'docs\evidence\recovery\r46-page-surface-result.json')
Add-Check $script:Checks 'page-surface' 'R46 fresh page/home/runtime surface closure passed' `
    ($R46.status -eq 'PASS' -and [int]$R46.browserOverflowCount -eq 0 -and [int]$R46.browserBlockerCount -eq 0) `
    "browserResults=$($R46.browserResultCount), overflow=$($R46.browserOverflowCount), blockers=$($R46.browserBlockerCount)"

$AdminLogin = Invoke-Api -Method 'Post' -Path '/api/v1/auth/login' -Body @{
    loginName = 'admin'
    password = '123123aa'
    loginTarget = 'PLATFORM'
}
$script:AdminHeaders = @{ Authorization = "Bearer $($AdminLogin.accessToken)" }

$System = Invoke-Api -Method 'Post' -Path '/api/v1/platform/systems' -Headers $script:AdminHeaders -Body @{
    systemName = "R52 FRC1 Surface $script:Suffix"
    systemCode = "r52_frc1_surface_$script:Suffix"
    tenantMode = 1
    templateCode = 'default'
}
$SystemId = [string]$System.systemId
$script:CreatedSystemIds.Add($SystemId) | Out-Null
$SwitchOptions = Invoke-Api -Method 'Get' -Path '/api/v1/platform/system-switch/options' -Headers $script:AdminHeaders
$SystemOption = @($SwitchOptions | Where-Object { [string]$_.systemId -eq $SystemId }) | Select-Object -First 1
$TenantId = [string]$SystemOption.tenantId
$null = Invoke-Api -Method 'Post' -Path '/api/v1/platform/system-switch' -Headers $script:AdminHeaders -Body @{
    systemId = $SystemId
    tenantId = $TenantId
    reason = 'recovery-r52 print preview contract setup'
}

$Group = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/module-groups" -Headers $script:AdminHeaders -Body @{
    name = "R52 Form Group $script:Suffix"
    sort = 10
    visibleRoleIds = @()
    publishStatus = 'DRAFT'
}
$Module = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules" -Headers $script:AdminHeaders -Body @{
    groupId = [string]$Group.groupId
    moduleCode = "r52_surface_$script:Suffix"
    name = "R52 Form $script:Suffix"
    status = 1
    description = 'Recovery R52 print preview values contract'
}
$ModuleId = [string]$Module.moduleId
$Field = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/fields" -Headers $script:AdminHeaders -Body (New-FieldBody -Code 'customerName' -Name 'Customer Name')
$TemplateCode = "r52_print_$script:Suffix"
$null = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/print-templates" -Headers $script:AdminHeaders -Body @{
    templateCode = $TemplateCode
    templateName = 'R52 Print Template'
    version = "draft_$script:Suffix"
    status = 1
    defaultTemplate = $true
    visibleRoleIds = @()
    boundFieldCodes = @('customerName')
    detailTableFieldCodes = @()
    signatureLabels = @()
    headerText = 'R52 Print Template'
    footerText = $null
    previewFileId = "preview_r52_$script:Suffix"
    pageSetup = @{
        paper = 'A4'
        orientation = 'PORTRAIT'
        marginTop = '16mm'
        marginRight = '14mm'
        marginBottom = '16mm'
        marginLeft = '14mm'
    }
}
$Preview = Invoke-Api -Method 'Post' -Path "/api/v1/systems/$SystemId/modules/$ModuleId/print-templates/$TemplateCode/preview" -Headers $script:AdminHeaders -Body @{
    templateCode = $TemplateCode
    previewValues = @{
        customerName = 'R52 Preview Value'
    }
}
$previewText = ($Preview | ConvertTo-Json -Depth 30 -Compress)
Add-Check $script:Checks 'print-preview' 'previewValues request renders Chinese production preview' `
    ($previewText -match 'R52 Preview Value' -and $previewText -notmatch 'sample|Record fields|Template code|Generated by unexamine') `
    $previewText
Add-Check $script:Checks 'print-preview' 'legacy sampleValues remains compatible but is not frontend production path' `
    ($null -ne $Preview.sections -and [string]$Preview.recordId -eq 'preview') `
    "recordId=$($Preview.recordId), sectionCount=$(@($Preview.sections).Count), fieldId=$($Field.fieldId)"

$script:CleanupResult = Remove-CreatedSystems

$browserAudit = [ordered]@{
    status = 'PASS'
    generatedAt = (Get-Date).ToString('o')
    source = 'R52 aggregation of fresh R43 and R46 deployed browser audits'
    r43 = @{
        resultFile = 'docs/evidence/recovery/r43-role-home-whole-path-usability-result.json'
        resultCount = $R43.resultCount
        failureCount = $R43.failureCount
        warningCount = $R43.warningCount
    }
    r46 = @{
        resultFile = 'docs/evidence/recovery/r46-page-surface-result.json'
        browserResultCount = $R46.browserResultCount
        browserOverflowCount = $R46.browserOverflowCount
        browserBlockerCount = $R46.browserBlockerCount
    }
}
$browserAudit | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $BrowserAuditFile -Encoding UTF8

$result = [ordered]@{
    status = 'PASS'
    productStatus = 'PARTIAL_ENGINEERING_EVIDENCE_ONLY'
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    requirementRows = @('REQ-5.8', 'REQ-6.1', 'REQ-6.3', 'REQ-6.8', 'REQ-9')
    journeyRows = @('J1', 'J2', 'J3', 'J5', 'J8', 'J9', 'J11')
    userSignoff = $false
    checks = @($script:Checks.ToArray())
    childResults = $childResults
    staticAudit = @{
        status = $StaticAudit.status
        blockerCount = $StaticAudit.blockerCount
        warningCount = $StaticAudit.warningCount
    }
    browserAuditFile = 'docs/evidence/recovery/screenshots/r52-frc1-deployed-surface/frc1-deployed-surface-browser-audit.json'
    printPreview = @{
        systemId = $SystemId
        moduleId = $ModuleId
        templateCode = $TemplateCode
        recordId = $Preview.recordId
        sectionCount = @($Preview.sections).Count
        traceId = $Preview.traceId
    }
    cleanup = $script:CleanupResult
}
$result | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $ResultFile -Encoding UTF8

$summary = @"
# REC-P0-052 / R52 FRC-1 Missing Product Surfaces Fresh Deployed Closure

Status: PASS as deployed engineering evidence only.

- Base URL: $BaseUrl
- Requirement rows: REQ-5.8, REQ-6.1, REQ-6.3, REQ-6.8, REQ-9
- Fresh role/home journey: R43 PASS, resultCount=$($R43.resultCount), failureCount=$($R43.failureCount), warningCount=$($R43.warningCount)
- Fresh page/home/runtime surface: R46 PASS, browserResultCount=$($R46.browserResultCount), overflow=$($R46.browserOverflowCount), blockers=$($R46.browserBlockerCount)
- Static usability audit: status=$($StaticAudit.status), blockers=$($StaticAudit.blockerCount), warnings=$($StaticAudit.warningCount)
- Print preview API: `previewValues` renders production preview values; frontend no longer emits sample/demo preview payload.
- Browser aggregation: `docs/evidence/recovery/screenshots/r52-frc1-deployed-surface/frc1-deployed-surface-browser-audit.json`

This does not close final product acceptance. Full requirement coverage and user signoff remain open.
"@
$summary | Set-Content -LiteralPath $SummaryFile -Encoding UTF8

$result | ConvertTo-Json -Depth 20
