param(
  [string]$BaseUrl = 'http://127.0.0.1:18131',
  [switch]$NoFailExit
)

$ErrorActionPreference = 'Stop'
$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$ResultPath = Join-Path $RepoRoot 'docs/evidence/recovery/r93-platform-flow-app-ia-boundary-result.json'
$SummaryPath = Join-Path $RepoRoot 'docs/evidence/recovery/r93-platform-flow-app-ia-boundary-2026-07-08.md'
$ScreenshotDir = Join-Path $RepoRoot 'docs/evidence/recovery/screenshots/r93-platform-flow-app-ia-boundary'
New-Item -ItemType Directory -Force -Path (Split-Path $ResultPath) | Out-Null
New-Item -ItemType Directory -Force -Path $ScreenshotDir | Out-Null

$checks = New-Object System.Collections.Generic.List[object]
$failures = New-Object System.Collections.Generic.List[string]

function Add-Check([string]$Name, [bool]$Passed, [string]$Detail) {
  $script:checks.Add([pscustomobject]@{ name = $Name; passed = $Passed; detail = $Detail }) | Out-Null
  if (-not $Passed) { $script:failures.Add("${Name}: $Detail") | Out-Null }
}

function Read-Text([string]$RelativePath) {
  return [System.IO.File]::ReadAllText((Join-Path $RepoRoot $RelativePath), [System.Text.UTF8Encoding]::new($false))
}

$platform = Read-Text 'frontend/src/features/platform/platformShell.ts'
$routes = Read-Text 'frontend/src/app/routes.ts'
$systemShell = Read-Text 'frontend/src/features/system-shell/systemShell.ts'
$css = Read-Text 'frontend/src/styles.css'
$ledger = Read-Text 'docs/framework/next-execution-ledger.md'
$taskCards = Read-Text 'docs/recovery/p0-task-cards.md'

$appsMatch = [regex]::Match($platform, 'function createPlatformAppsPage\(navigate: Navigate\): HTMLElement \{[\s\S]*?(?=function createPlatformFlowPage)', [System.Text.RegularExpressions.RegexOptions]::Singleline)
$flowMatch = [regex]::Match($platform, 'function createPlatformFlowPage\(navigate: Navigate\): HTMLElement \{[\s\S]*?(?=function createPlatformAiPage)', [System.Text.RegularExpressions.RegexOptions]::Singleline)
$headerMatch = [regex]::Match($platform, 'function createPlatformHeader\(navigate: Navigate\): HTMLElement \{[\s\S]*?(?=function createPlatformWorkbench)', [System.Text.RegularExpressions.RegexOptions]::Singleline)

Add-Check 'R93 task recorded in ledger' ($ledger -like '*REC-P0-093 Platform Flow And Application IA Boundary Realignment*' -and ($ledger -like '*| active | REC-P0-093*' -or $ledger -like '*| accepted | REC-P0-093*')) 'next-execution ledger must record R93 as active or accepted.'
$r92AfterR93Ok = ($ledger -like '*| active | REC-P0-092 Workflow Designer Advanced Node Publish Impact Closure*') -or (($ledger -like '*| accepted | REC-P0-092 Workflow Designer Advanced Node Publish Impact Closure*') -and (($ledger -like '*| active | REC-P0-094 Fresh Deployed Role Journey Audit After R92/R93*') -or ($ledger -like '*| active | REC-P0-095 Platform Workbench System Entry Density And List Rewrite Closure*')))
Add-Check 'R92/R94/R95 sequencing after R93 acceptance' $r92AfterR93Ok 'R92 must either be active immediately after R93 or accepted with R94/R95 active as the follow-up deployed audit.'
Add-Check 'R93 task card exists' ($taskCards -like '*REC-P0-093 Platform Flow And Application IA Boundary Realignment*') 'task card must persist the new user feedback and boundary.'
Add-Check 'platform apps function found' $appsMatch.Success 'createPlatformAppsPage must be statically inspectable.'
if ($appsMatch.Success) {
  $appsBody = $appsMatch.Value
  Add-Check 'apps page separated marker' ($appsBody -like '*platformAppsSeparatedFromSystemEntry*' -and $appsBody -like '*platformAppsSystemEntryCount*') 'apps page must expose explicit no-system-entry markers.'
  Add-Check 'apps page no system switch panel' ($appsBody -notlike '*createSystemSwitchPanel*') 'apps page must not render the system switch panel.'
  Add-Check 'apps page no system cards' ($appsBody -notlike '*platformSystemCard*') 'apps page must not render platform system cards.'
  Add-Check 'apps page has application list' ($appsBody -like '*platformApplicationList*' -and $platform -like '*createPlatformApplicationRows*') 'apps page must show application rows/config affordances.'  Add-Check 'apps copy rejects system entry' (($appsBody -like '*\\u4e0d\\u662f\\u8fdb\\u5165\\u7cfb\\u7edf*') -or ($appsBody -like '*not system entry*')) 'visible copy must state app is not system entry.'
}
Add-Check 'flow function found' $flowMatch.Success 'createPlatformFlowPage must be statically inspectable.'
if ($flowMatch.Success) {
  $flowBody = $flowMatch.Value
  Add-Check 'flow independent markers' ($flowBody -like '*platformFlowSeparatedFromApplication*' -and $flowBody -like '*platformFlowList*') 'Flow page must expose independent module/list markers.'  Add-Check 'flow copy rejects app/system entry' (($flowBody -like '*\\u4e0d\\u627f\\u8f7d\\u5e94\\u7528\\u5217\\u8868*') -and ($flowBody -like '*\\u4e0d\\u662f\\u7cfb\\u7edf\\u5165\\u53e3*')) 'Flow page must state its boundary.'
}
Add-Check 'platform header found' $headerMatch.Success 'createPlatformHeader must be statically inspectable.'
if ($headerMatch.Success) {
  $headerBody = $headerMatch.Value
  Add-Check 'primary platform nav follows temp_flow' ($headerBody -like '*platformPrimaryNav*' -and $headerBody -like "*'/platform/flow'*" -and $headerBody -like "*'/platform/apps'*" -and $headerBody -like "*'/platform/work'*") 'primary nav must include workbench/Flow/apps/work.'
  Add-Check 'auxiliary platform nav separated' ($headerBody -like '*platformAuxNav*' -and $headerBody -like '*platformAuxItem*system-entry*') 'AI/todo/message/profile/system-entry must be auxiliary, not app content.'
}
Add-Check 'system entry remains on workbench' ($platform -like '*platformSystemEntryPanel*' -and $platform -like '*createSystemSwitchPanel*') 'system entry panel must still exist for /platform workbench.'
Add-Check 'platform work route registered' ($routes -like "*'/platform/work'*") 'routes.ts must include /platform/work.'
Add-Check 'system admin deep section route' ($systemShell -like '*systemAdminSectionFromRoute*' -and $systemShell -like '*renderSystemAdmin(navigate, systemAdminSectionFromRoute(route))*') 'system shell must support /systems/{id}/admin/openapi-apps.'
Add-Check 'responsive platform styles present' ($css -like '*platform-workspace-header*' -and $css -like '*platform-application-table*') 'styles must include platform IA responsive support.'

$deployed = [pscustomobject]@{ checked = $false; reachable = $false; assetChecked = $false; detail = 'BaseUrl was not reachable or no deployed check was requested.' }
try {
  $health = Invoke-RestMethod -Uri "$BaseUrl/api/v1/health" -Method Get -TimeoutSec 5
  $deployed.checked = $true
  $deployed.reachable = $true
  $deployed.detail = "health=$($health.status)"
} catch {
  $deployed.checked = $true
  $deployed.detail = $_.Exception.Message
}

$status = if ($failures.Count -eq 0) { 'PASS' } else { 'FAIL' }
$result = [pscustomobject]@{
  status = $status
  batch = 'RECOVERY-R93'
  baseUrl = $BaseUrl
  userSignoff = $false
  checkedAt = (Get-Date).ToString('o')
  checks = $checks
  failures = $failures
  deployed = $deployed
}
$result | ConvertTo-Json -Depth 10 | Set-Content -Path $ResultPath -Encoding UTF8

$summary = @()
$summary += '# R93 Platform Flow/Application IA Boundary Evidence'
$summary += ''
$summary += "Status: ``$status``"
$summary += ''
$summary += 'This is engineering evidence only. It does not set or imply user signoff.'
$summary += ''
$summary += '## Checks'
foreach ($check in $checks) {
  $mark = if ($check.passed) { 'PASS' } else { 'FAIL' }
  $summary += "- $mark - $($check.name): $($check.detail)"
}
if ($failures.Count -gt 0) {
  $summary += ''
  $summary += '## Failures'
  foreach ($failure in $failures) { $summary += "- $failure" }
}
$summary += ''
$summary += "Result JSON: ``$ResultPath``"
$summary += "User signoff: ``false``"
$summary -join "`r`n" | Set-Content -Path $SummaryPath -Encoding UTF8

Write-Host "R93_STATUS=$status"
Write-Host "R93_RESULT=$ResultPath"
if ($status -ne 'PASS' -and -not $NoFailExit) { exit 1 }






