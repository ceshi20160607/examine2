$ErrorActionPreference = 'Stop'
$workspace = (Resolve-Path "$PSScriptRoot/../../../..").Path
$frontend = Join-Path $workspace 'frontend'
$outLog = Join-Path $PSScriptRoot 'frontend-task-03.out.log'
$errLog = Join-Path $PSScriptRoot 'frontend-task-03.err.log'
$process = Start-Process -FilePath 'npm.cmd' -ArgumentList 'run','dev','--','--port','5175' `
    -WorkingDirectory $frontend -RedirectStandardOutput $outLog -RedirectStandardError $errLog `
    -WindowStyle Hidden -PassThru
$status = 0
for ($attempt = 0; $attempt -lt 30; $attempt++) {
    try {
        $response = Invoke-WebRequest -UseBasicParsing 'http://127.0.0.1:5175' -TimeoutSec 2
        $status = $response.StatusCode
        if ($status -eq 200) { break }
    } catch { }
    Start-Sleep -Milliseconds 500
}
[ordered]@{ pid = $process.Id; status = $status; outLog = $outLog; errLog = $errLog } | ConvertTo-Json
if ($status -ne 200) { exit 1 }
