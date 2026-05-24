# Forward to tests/api (kept for backward compatibility)
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot '..\..\tests\api\e2e-smoke.ps1') @args
exit $LASTEXITCODE
