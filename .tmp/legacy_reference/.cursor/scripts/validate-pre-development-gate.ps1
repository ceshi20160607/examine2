param(
    [string]$RepositoryRoot = (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)),
    [string]$Gate = '.cursor/session/baseline/pre-development-gate.json'
)

$ErrorActionPreference = 'Stop'
$rootPath = (Resolve-Path -LiteralPath $RepositoryRoot).Path

function Resolve-RepositoryPath([string]$Value, [string]$Label, [bool]$MustExist = $true) {
    if ([string]::IsNullOrWhiteSpace($Value)) {
        throw "$Label is empty."
    }
    $candidate = if ([System.IO.Path]::IsPathRooted($Value)) {
        [System.IO.Path]::GetFullPath($Value)
    } else {
        [System.IO.Path]::GetFullPath((Join-Path $rootPath $Value))
    }
    $prefix = $rootPath.TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar
    if (-not $candidate.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "$Label is outside the repository: $Value"
    }
    if ($MustExist -and -not (Test-Path -LiteralPath $candidate -PathType Leaf)) {
        throw "$Label does not exist: $Value"
    }
    return $candidate
}

function Assert-ExactKeys([object]$Value, [string[]]$Expected, [string]$Label) {
    $actual = @($Value.PSObject.Properties.Name | Sort-Object)
    $wanted = @($Expected | Sort-Object)
    if (($actual -join '|') -ne ($wanted -join '|')) {
        throw "$Label keys mismatch; expected=$($wanted -join ',') actual=$($actual -join ',')"
    }
}

function Assert-FileHash([string]$Path, [string]$Expected, [string]$Label) {
    $actual = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $Expected) {
        throw "$Label hash mismatch; expected=$Expected actual=$actual"
    }
}

$gatePath = Resolve-RepositoryPath $Gate 'Pre-development gate'
$gateValue = Get-Content -Raw -Encoding UTF8 $gatePath | ConvertFrom-Json
Assert-ExactKeys $gateValue @('schemaVersion', 'gateId', 'scopeId', 'generatedAt', 'engineeringVerdict', 'ownerVerdict', 'codingAuthorized', 'source', 'deliveryContract', 'requiredArtifactIds', 'checks', 'nextAction') 'Pre-development gate'
if ($gateValue.schemaVersion -ne 1 -or $gateValue.gateId -ne 'GATE-PRE-DEVELOPMENT') {
    throw 'Pre-development gate identity is invalid.'
}
if ($gateValue.engineeringVerdict -ne 'pass') {
    throw 'Engineering verdict must be pass before this gate can be reviewed.'
}
if ($gateValue.ownerVerdict -notin @('pending', 'accepted')) {
    throw 'Owner verdict must be pending or accepted.'
}
if ([bool]$gateValue.codingAuthorized -ne ($gateValue.ownerVerdict -eq 'accepted')) {
    throw 'codingAuthorized must be true exactly when ownerVerdict is accepted.'
}

$sourcePath = Resolve-RepositoryPath ([string]$gateValue.source.path) 'Authoritative requirement source'
Assert-FileHash $sourcePath ([string]$gateValue.source.sha256) 'Authoritative requirement source'
$deliveryPath = Resolve-RepositoryPath ([string]$gateValue.deliveryContract.path) 'Delivery contract'
Assert-FileHash $deliveryPath ([string]$gateValue.deliveryContract.sha256) 'Delivery contract'

$deliveryValidator = Resolve-RepositoryPath '.cursor/skills/compile-project-delivery/scripts/validate_delivery_plan.py' 'Delivery validator'
& python $deliveryValidator $deliveryPath
if ($LASTEXITCODE -ne 0) {
    throw 'Delivery contract validation failed.'
}
$delivery = Get-Content -Raw -Encoding UTF8 $deliveryPath | ConvertFrom-Json
if ($delivery.source.path -ne $gateValue.source.path -or $delivery.source.sha256 -ne $gateValue.source.sha256) {
    throw 'Delivery contract source binding differs from the gate source.'
}

$artifactIndex = @{}
foreach ($artifact in @($delivery.artifacts)) {
    $artifactIndex[[string]$artifact.id] = $artifact
}
$requiredArtifactIds = @($gateValue.requiredArtifactIds)
if ($requiredArtifactIds.Count -eq 0 -or @($requiredArtifactIds | Sort-Object -Unique).Count -ne $requiredArtifactIds.Count) {
    throw 'requiredArtifactIds must be a non-empty unique list.'
}
foreach ($artifactId in $requiredArtifactIds) {
    if (-not $artifactIndex.ContainsKey([string]$artifactId)) {
        throw "Required artifact is absent from delivery contract: $artifactId"
    }
    $artifact = $artifactIndex[[string]$artifactId]
    if ($artifact.status -ne 'accepted') {
        throw "Required artifact is not accepted: $artifactId"
    }
    $artifactPath = Resolve-RepositoryPath ([string]$artifact.path) "Artifact $artifactId"
    Assert-FileHash $artifactPath ([string]$artifact.sha256) "Artifact $artifactId"
}

$expectedCheckIds = @(
    'base-instance-sync',
    'delivery-traceability',
    'artifact-hashes',
    'management-ui-contract',
    'test-plan-structure',
    'verify-command-execution',
    'backend-boundary-contract',
    'legacy-evidence-isolation',
    'no-core-performance'
)
$declaredChecks = @($gateValue.checks)
if ((@($declaredChecks.id | Sort-Object) -join '|') -ne (@($expectedCheckIds | Sort-Object) -join '|')) {
    throw 'Pre-development gate check set is incomplete or contains unknown checks.'
}
foreach ($check in $declaredChecks) {
    Assert-ExactKeys $check @('id', 'status', 'evidence') "Check $($check.id)"
    if ($check.status -ne 'pass' -or [string]::IsNullOrWhiteSpace([string]$check.evidence)) {
        throw "Check $($check.id) must declare pass with evidence."
    }
}

$frameworkValidator = Resolve-RepositoryPath '.cursor/scripts/validate-framework.ps1' 'Framework validator'
& powershell -NoProfile -ExecutionPolicy Bypass -File $frameworkValidator -Root (Join-Path $rootPath '.cursor') -Instance
if ($LASTEXITCODE -ne 0) {
    throw 'Base instance validation failed.'
}

$uiArtifact = $artifactIndex['ART-RUNTIME-MODULE-LIST-UI']
if ($null -eq $uiArtifact) {
    throw 'ART-RUNTIME-MODULE-LIST-UI is required.'
}
$uiValidator = Resolve-RepositoryPath '.cursor/skills/build-management-ui/scripts/validate_ui_contract.py' 'UI contract validator'
$uiPath = Resolve-RepositoryPath ([string]$uiArtifact.path) 'Runtime management UI contract'
& python $uiValidator $uiPath
if ($LASTEXITCODE -ne 0) {
    throw 'Runtime management UI contract validation failed.'
}

$testArtifacts = @($delivery.artifacts | Where-Object { $_.id -match '^ART-P1-.+-TEST$' })
if ($testArtifacts.Count -ne @($delivery.cycles).Count) {
    throw 'Every detailed P1 cycle must have exactly one accepted cycle test-plan artifact.'
}
$testPlanValidator = Resolve-RepositoryPath '.cursor/skills/verify-by-delivery-level/scripts/validate_test_plan.py' 'Test-plan validator'
$testPlanScopeIndex = @{}
foreach ($artifact in $testArtifacts) {
    if ($artifact.status -ne 'accepted') {
        throw "Test-plan artifact is not accepted: $($artifact.id)"
    }
    $testPlanPath = Resolve-RepositoryPath ([string]$artifact.path) "Test-plan artifact $($artifact.id)"
    & python $testPlanValidator $testPlanPath
    if ($LASTEXITCODE -ne 0) {
        throw "Test-plan validation failed: $($artifact.id)"
    }
    $testPlan = Get-Content -Raw -Encoding UTF8 $testPlanPath | ConvertFrom-Json
    if ($testPlanScopeIndex.ContainsKey([string]$testPlan.scopeId)) {
        throw "Duplicate test-plan scope: $($testPlan.scopeId)"
    }
    $testPlanScopeIndex[[string]$testPlan.scopeId] = [string]$artifact.id
}

$cycleIds = @($delivery.cycles.id | Sort-Object)
if (($cycleIds -join '|') -ne (@($testPlanScopeIndex.Keys | Sort-Object) -join '|')) {
    throw 'Cycle IDs and cycle test-plan scope IDs differ.'
}

$forbiddenPattern = '(?i)(failIfNoSpecifiedTests\s*=\s*false|performance|benchmark|load[-_ ]?test|stress|million|million-record|k6|jmeter|gatling)'
foreach ($task in @($delivery.tasks)) {
    if ([int]$task.estimateMinutes -gt 120) {
        throw "Task exceeds 120 minutes: $($task.id)"
    }
    if ([string]$task.acceptanceCommand -match $forbiddenPattern) {
        throw "Task contains a forbidden acceptance command: $($task.id)"
    }
    if ($task.id -match '-VERIFY$') {
        if ([string]$task.acceptanceCommand -notmatch '(?i)run-verified-test-plan\.ps1') {
            throw "VERIFY task does not use the execution runner: $($task.id)"
        }
        $expectedEvidence = ".cursor/session/evidence/baseline/$($task.cycleId)"
        if (@($task.writeScope).Count -ne 1 -or [string]$task.writeScope[0] -ne $expectedEvidence) {
            throw "VERIFY task evidence scope is not isolated: $($task.id)"
        }
        if ([string]$task.acceptanceCommand -notmatch [regex]::Escape($expectedEvidence)) {
            throw "VERIFY task command does not bind its exact evidence root: $($task.id)"
        }
        if (-not $testPlanScopeIndex.ContainsKey([string]$task.cycleId) -or [string]$testPlanScopeIndex[[string]$task.cycleId] -notin @($task.inputArtifactIds)) {
            throw "VERIFY task does not consume its cycle test plan: $($task.id)"
        }
    }
}
foreach ($cycle in @($delivery.cycles)) {
    if ([int]$cycle.timeboxMinutes -gt 240) {
        throw "Cycle exceeds 240 minutes: $($cycle.id)"
    }
}

$boundaryArtifact = $artifactIndex['ART-P1-BACKEND-BOUNDARY']
$boundaryPath = Resolve-RepositoryPath ([string]$boundaryArtifact.path) 'P1 backend boundary contract'
$boundary = Get-Content -Raw -Encoding UTF8 $boundaryPath | ConvertFrom-Json
if ($boundary.status -ne 'accepted') {
    throw 'P1 backend boundary contract is not accepted.'
}
$forbiddenDependencies = @($boundary.forbiddenBusinessDependencies)
foreach ($required in @('base.mapper', 'JdbcTemplate', 'ResultSet', 'inline SQL')) {
    if ($required -notin $forbiddenDependencies) {
        throw "P1 backend boundary is missing forbidden dependency: $required"
    }
}
$expectedChain = @('controller', 'use_case_service', 'generated_IService', 'generated_ServiceImpl', 'generated_BaseMapper', 'database')
if ((@($boundary.ordinaryCallChain) -join '|') -ne ($expectedChain -join '|')) {
    throw 'P1 ordinary backend call chain differs from the Base/manage contract.'
}

if ($gateValue.ownerVerdict -eq 'accepted') {
    Write-Output "PRE_DEVELOPMENT_GATE_PASS $($gateValue.scopeId) codingAuthorized=true"
} else {
    Write-Output "PRE_DEVELOPMENT_ENGINEERING_PASS $($gateValue.scopeId) ownerVerdict=pending codingAuthorized=false"
}
