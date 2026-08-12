param(
    [string]$ResultPath = "$PSScriptRoot/task-03-build.json"
)

$ErrorActionPreference = 'Stop'
$workspace = (Resolve-Path "$PSScriptRoot/../../../..").Path
$dist = Join-Path $workspace 'frontend/dist-p4-c4'
if (-not (Test-Path -LiteralPath $dist -PathType Container)) { throw 'dist-p4-c4 is missing' }
$files = @(Get-ChildItem -LiteralPath $dist -Recurse -File | Sort-Object FullName | ForEach-Object {
    [ordered]@{
        path = $_.FullName.Substring($dist.Length + 1).Replace('\','/')
        bytes = $_.Length
        sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    }
})
$treeText = ($files | ForEach-Object { "$($_.path):$($_.bytes):$($_.sha256)" }) -join "`n"
$treeBytes = [Text.Encoding]::UTF8.GetBytes($treeText)
$treeDigest = [Security.Cryptography.SHA256]::Create().ComputeHash($treeBytes)
$treeHash = ([BitConverter]::ToString($treeDigest) -replace '-','').ToLowerInvariant()
$listener = netstat -ano | Select-String ':5175\s+.*LISTENING' | Select-Object -First 1
if ($null -eq $listener) { throw 'Production preview is not listening on 5175' }
$previewPid = [int](($listener.ToString().Trim() -split '\s+')[-1])
$preview = Get-Process -Id $previewPid
$sourcePaths = @(
    'frontend/src/views/system/admin/configStudioModel.ts',
    'frontend/src/views/system/admin/SystemConfigStudioView.vue',
    'frontend/src/views/system/runtimeRecordModel.ts',
    'frontend/src/views/system/RuntimeDerivedField.vue',
    'frontend/src/views/system/SystemWorkbenchView.vue',
    'frontend/tests/unit/config-studio-model.spec.ts',
    'frontend/tests/unit/runtime-derived-field.spec.ts',
    'frontend/tests/unit/runtime-record-model.spec.ts',
    'frontend/tests/e2e/p4-c4.spec.ts'
)
$sources = [ordered]@{}
foreach ($relative in $sourcePaths) {
    $sources[$relative] = (Get-FileHash -LiteralPath (Join-Path $workspace $relative) -Algorithm SHA256).Hash.ToLowerInvariant()
}
$jar = Join-Path $workspace 'backend/examine-web/target/examine-web-1.0.0-SNAPSHOT.jar'
$result = [ordered]@{
    checkedAt = (Get-Date).ToString('o'); verdict = 'pass'; output = 'frontend/dist-p4-c4'
    treeSha256 = $treeHash; fileCount = $files.Count; files = $files
    toolchain = [ordered]@{
        node = (& node --version).Trim(); npm = (& npm.cmd --version).Trim()
    }
    preview = [ordered]@{
        url = 'http://127.0.0.1:5175'; pid = $previewPid
        process = $preview.ProcessName; startedAt = $preview.StartTime.ToString('o')
    }
    backendJarSha256 = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant()
    sources = $sources
}
[IO.File]::WriteAllText($ResultPath, ($result | ConvertTo-Json -Depth 50), [Text.UTF8Encoding]::new($false))
$result | ConvertTo-Json -Depth 5
