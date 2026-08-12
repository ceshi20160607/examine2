param(
    [string]$Root = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
$rootPath = (Resolve-Path -LiteralPath $Root).Path
$python = Get-Command python -ErrorAction Stop

$pythonChecks = @(
    @('skills/compile-project-delivery/scripts/validate_delivery_plan.py', '--self-test'),
    @('skills/compile-project-delivery/scripts/validate_delivery_plan.py', 'skills/compile-project-delivery/references/example.json', '--source-root', '.'),
    @('skills/build-management-ui/scripts/validate_ui_contract.py', '--self-test'),
    @('skills/build-management-ui/scripts/validate_ui_contract.py', 'skills/build-management-ui/references/example.json'),
    @('skills/implement-backend-module/scripts/audit_backend_module.py', '--self-test'),
    @('skills/verify-by-delivery-level/scripts/validate_test_plan.py', '--self-test')
)

Push-Location -LiteralPath $rootPath
try {
    foreach ($check in $pythonChecks) {
        $scriptPath = Join-Path $rootPath $check[0]
        $arguments = @($scriptPath)
        foreach ($argument in @($check | Select-Object -Skip 1)) {
            if ([string]$argument -like '--*' -or [string]$argument -eq '.') {
                $arguments += [string]$argument
            } else {
                $arguments += (Join-Path $rootPath ([string]$argument))
            }
        }
        & $python.Source @arguments | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "Base contract check failed: $($check -join ' ')"
        }
    }
} finally {
    Pop-Location
}

Write-Output 'PASS: Base delivery, management UI, backend boundary and layered test contracts are valid.'
