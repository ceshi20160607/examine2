param(
    [string]$BaseUrl = 'http://127.0.0.1:18131',
    [string]$ResultPath = 'docs/evidence/recovery/r59-frc2-frc6-admin-configuration-human-first-use-result.json'
)

$ErrorActionPreference = 'Stop'

function Add-Check {
    param(
        [System.Collections.Generic.List[object]]$Checks,
        [string]$Name,
        [bool]$Passed,
        [string]$Detail
    )
    $Checks.Add([ordered]@{
        name = $Name
        passed = $Passed
        detail = $Detail
    }) | Out-Null
}

function Invoke-Step {
    param(
        [string]$Name,
        [string]$Command,
        [string]$WorkingDirectory = (Get-Location).Path
    )
    $psi = [System.Diagnostics.ProcessStartInfo]::new()
    $psi.FileName = 'powershell'
    $encodedCommand = [Convert]::ToBase64String([System.Text.Encoding]::Unicode.GetBytes($Command))
    $psi.Arguments = "-NoProfile -EncodedCommand $encodedCommand"
    $psi.WorkingDirectory = $WorkingDirectory
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.UseShellExecute = $false
    $process = [System.Diagnostics.Process]::Start($psi)
    $stdout = $process.StandardOutput.ReadToEnd()
    $stderr = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    return [ordered]@{
        name = $Name
        exitCode = $process.ExitCode
        stdoutTail = if ($stdout.Length -gt 1200) { $stdout.Substring($stdout.Length - 1200) } else { $stdout }
        stderrTail = if ($stderr.Length -gt 1200) { $stderr.Substring($stderr.Length - 1200) } else { $stderr }
    }
}

function Read-JsonFile {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Expected result file was not found: $Path"
    }
    return (Get-Content -Raw -Encoding UTF8 -LiteralPath $Path | ConvertFrom-Json)
}

$checks = [System.Collections.Generic.List[object]]::new()
$adminSource = Get-Content -Raw -LiteralPath 'frontend/src/features/system-admin/systemAdmin.ts'
$runtimeSource = Get-Content -Raw -LiteralPath 'frontend/src/features/runtime/records/runtimeRecords.ts'
$stylesSource = Get-Content -Raw -LiteralPath 'frontend/src/styles.css'
$liveDataSource = Get-Content -Raw -LiteralPath 'frontend/src/api/liveData.ts'

Add-Check $checks 'module configuration uses one active task surface' (
    $adminSource -match 'type ModuleWorkspaceTab' `
    -and $adminSource -match 'module-work-tabs' `
    -and $adminSource -match 'moduleWorkActiveTab' `
    -and $adminSource -match 'renderActiveTab'
) 'module lifecycle, fields, list/import-export, page, and print are segmented instead of stacked'

Add-Check $checks 'module configuration still exposes required no-code tasks' (
    $adminSource -match 'createModuleLifecyclePanel' `
    -and $adminSource -match 'createFieldBuilder' `
    -and $adminSource -match 'createSceneAndImportExportPanel' `
    -and $adminSource -match 'createModulePageDesigner' `
    -and $adminSource -match 'createPrintTemplateDesigner' `
    -and $adminSource -match 'runModulePublishCheck' `
    -and $adminSource -match 'publishSystemModule'
) 'segmentation did not remove lifecycle, field, list, page, print, publish-check, or publish capability'

Add-Check $checks 'module workspace no longer renders all configuration panels in one return block' (
    -not ($adminSource.Contains("createModuleLifecyclePanel(systemId, module, groups, reload, result),") `
        -and $adminSource.Contains("createFieldBuilder(systemId, module, dictTypes, result),") `
        -and $adminSource.Contains("createSceneAndImportExportPanel(systemId, module, result),") `
        -and $adminSource.Contains("createModulePageDesigner(systemId, module, reload),") `
        -and $adminSource.Contains("createPrintTemplateDesigner(systemId, module, result),"))
) 'the previous broad stacked module workspace is removed'

Add-Check $checks 'permission preview and runtime reflection contracts remain wired' (
    $adminSource -match 'previewSystemEffectivePermission' `
    -and $adminSource -match 'saveSystemRolePermissions' `
    -and $runtimeSource -match 'loadRuntimeLiveData' `
    -and $runtimeSource -match 'currentSchema' `
    -and $liveDataSource -match 'loadRuntimeModulePageSchema' `
    -and $liveDataSource -match 'loadSystemModuleListSchema'
) 'R59 next work can prove admin permission preview and normal runtime schema reflection from the same configuration'

Add-Check $checks 'responsive module task tabs are styled' (
    $stylesSource -match 'module-work-tabs' `
    -and $stylesSource -match 'module-work-active-task' `
    -and $stylesSource -match '@media \(max-width: 900px\)' `
    -and $stylesSource -match '\.module-work-tabs,\s*\r?\n\s*\.field-builder'
) 'desktop and mobile CSS constrain the segmented module task surface'

$steps = @()
$frontendDir = Join-Path (Get-Location) 'frontend'
$steps += Invoke-Step -Name 'npm typecheck' -Command 'npm.cmd run typecheck' -WorkingDirectory $frontendDir
$steps += Invoke-Step -Name 'npm build' -Command 'npm.cmd run build' -WorkingDirectory $frontendDir
$steps += Invoke-Step -Name 'verify release' -Command "powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-release.ps1 -BaseUrl '$BaseUrl' -CheckDeployedFrontend"
$steps += Invoke-Step -Name 'final usability static audit' -Command 'powershell -NoProfile -ExecutionPolicy Bypass -File scripts/final-usability-static-audit.ps1'
$steps += Invoke-Step -Name 'R59 admin first-use browser audit' -Command "powershell -NoProfile -ExecutionPolicy Bypass -File scripts/recovery-r59-admin-first-use-browser-smoke.ps1 -BaseUrl '$BaseUrl'"
$steps += Invoke-Step -Name 'R53 fresh no-code configuration chain' -Command "powershell -NoProfile -ExecutionPolicy Bypass -File scripts/recovery-r53-frc2-no-code-configuration-coherence.ps1 -BaseUrl '$BaseUrl'"

foreach ($step in $steps) {
    Add-Check $checks $step.name ($step.exitCode -eq 0) "exitCode=$($step.exitCode)"
}

$staticAudit = $null
$browserAudit = $null
$r53 = $null
try {
    $staticAudit = Read-JsonFile 'docs/evidence/final-usability-static-audit-result.json'
    Add-Check $checks 'static audit has no visible blocker or warning findings' (
        $staticAudit.status -eq 'PASS' -and [int]$staticAudit.blockerCount -eq 0 -and [int]$staticAudit.warningCount -eq 0
    ) "status=$($staticAudit.status), blockers=$($staticAudit.blockerCount), warnings=$($staticAudit.warningCount)"
} catch {
    Add-Check $checks 'static audit has no visible blocker or warning findings' $false $_.Exception.Message
}
try {
    $browserAudit = Read-JsonFile 'docs/evidence/recovery/r59-admin-first-use-browser-result.json'
    $tabsCovered = @($browserAudit.activeTabsCovered | ForEach-Object { [string]$_ })
    Add-Check $checks 'deployed browser first-use module workspace exposes one active task at a time' (
        $browserAudit.status -eq 'PASS' `
        -and [int]$browserAudit.browserResultCount -ge 10 `
        -and [int]$browserAudit.browserBlockerCount -eq 0 `
        -and [int]$browserAudit.browserOverflowCount -eq 0 `
        -and ($tabsCovered -contains 'lifecycle') `
        -and ($tabsCovered -contains 'fields') `
        -and ($tabsCovered -contains 'scene') `
        -and ($tabsCovered -contains 'page') `
        -and ($tabsCovered -contains 'print')
    ) "results=$($browserAudit.browserResultCount), blockers=$($browserAudit.browserBlockerCount), overflow=$($browserAudit.browserOverflowCount), tabs=$($tabsCovered -join ',')"
} catch {
    Add-Check $checks 'deployed browser first-use module workspace exposes one active task at a time' $false $_.Exception.Message
}
try {
    $r53 = Read-JsonFile 'docs/evidence/recovery/r53-frc2-no-code-configuration-coherence-result.json'
    Add-Check $checks 'fresh no-code configuration chain still closes API readback permission and runtime reflection' (
        $r53.status -eq 'PASS' `
        -and [int]$r53.moduleConfig.schemaColumnCount -ge 3 `
        -and [int]$r53.permissionPreview.normalSchemaColumnCount -gt 0 `
        -and $r53.permissionPreview.normalSecretColumnLeaked -eq $false `
        -and [int]$r53.fieldDictMenu.fieldCount -ge 12 `
        -and [int]$r53.fieldDictMenu.normalSchemaColumnCount -gt 0
    ) "moduleColumns=$($r53.moduleConfig.schemaColumnCount), normalSchema=$($r53.permissionPreview.normalSchemaColumnCount), fields=$($r53.fieldDictMenu.fieldCount)"
} catch {
    Add-Check $checks 'fresh no-code configuration chain still closes API readback permission and runtime reflection' $false $_.Exception.Message
}

$failed = @($checks | Where-Object { -not $_.passed })
$result = [ordered]@{
    status = if ($failed.Count -eq 0) { 'PASS' } else { 'FAIL' }
    productStatus = if ($failed.Count -eq 0) { 'R59_ACCEPTED_AS_DEPLOYED_ENGINEERING_EVIDENCE_ONLY' } else { 'R59_IN_PROGRESS_ENGINEERING_EVIDENCE_ONLY' }
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    coverageRows = @('REQ-4.3','REQ-5.3','REQ-5.3.1','REQ-5.4','REQ-5.5','REQ-5.6','REQ-5.7','REQ-5.9','REQ-5.10','REQ-6.2','REQ-6.7','REQ-6.10')
    userSignoff = $false
    accepted = $failed.Count -eq 0
    checks = @($checks)
    childResults = $steps
    staticAudit = if ($null -eq $staticAudit) { $null } else { @{
        status = $staticAudit.status
        blockerCount = $staticAudit.blockerCount
        warningCount = $staticAudit.warningCount
    } }
    browserFirstUse = if ($null -eq $browserAudit) { $null } else { @{
        resultFile = 'docs/evidence/recovery/r59-admin-first-use-browser-result.json'
        browserResultCount = $browserAudit.browserResultCount
        browserOverflowCount = $browserAudit.browserOverflowCount
        browserBlockerCount = $browserAudit.browserBlockerCount
        activeTabsCovered = @($browserAudit.activeTabsCovered)
        browserAuditPath = $browserAudit.browserAuditPath
    } }
    noCodeConfigurationChain = if ($null -eq $r53) { $null } else { @{
        resultFile = 'docs/evidence/recovery/r53-frc2-no-code-configuration-coherence-result.json'
        moduleConfig = $r53.moduleConfig
        permissionPreview = $r53.permissionPreview
        fieldDictMenu = $r53.fieldDictMenu
    } }
}

$resultDir = Split-Path -Parent $ResultPath
if ($resultDir -and -not (Test-Path -LiteralPath $resultDir)) {
    New-Item -ItemType Directory -Force -Path $resultDir | Out-Null
}
$json = $result | ConvertTo-Json -Depth 12
[System.IO.File]::WriteAllText((Join-Path (Get-Location) $ResultPath), $json, [System.Text.UTF8Encoding]::new($false))
$json

if ($result.status -ne 'PASS') {
    exit 1
}
