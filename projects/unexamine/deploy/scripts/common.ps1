Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Resolve-DeploymentRoot {
    param([Parameter(Mandatory = $true)][string]$Root)
    $resolved = [System.IO.Path]::GetFullPath($Root)
    if ([System.IO.Path]::GetPathRoot($resolved) -eq $resolved) {
        throw "Deployment root cannot be a drive root: $resolved"
    }
    return $resolved
}

function Import-DeploymentEnvironment {
    param([Parameter(Mandatory = $true)][string]$Path)
    $resolved = [System.IO.Path]::GetFullPath($Path)
    if (-not (Test-Path -LiteralPath $resolved -PathType Leaf)) { throw "Environment file not found: $resolved" }
    foreach ($line in Get-Content -LiteralPath $resolved) {
        $text = $line.Trim()
        if (-not $text -or $text.StartsWith('#')) { continue }
        $parts = $text.Split('=', 2)
        if ($parts.Count -ne 2) { throw "Invalid environment line: $text" }
        [Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1], 'Process')
    }
}

function Get-CurrentReleaseDirectory {
    param([Parameter(Mandatory = $true)][string]$Root)
    $pointer = Join-Path $Root 'runtime/current-release.txt'
    if (-not (Test-Path -LiteralPath $pointer -PathType Leaf)) { throw "Current release pointer is missing: $pointer" }
    $version = (Get-Content -Raw -LiteralPath $pointer).Trim()
    if ($version -notmatch '^[0-9A-Za-z._-]{1,100}$') { throw "Current release pointer is invalid" }
    $release = [System.IO.Path]::GetFullPath((Join-Path $Root "releases/$version"))
    $releasesRoot = [System.IO.Path]::GetFullPath((Join-Path $Root 'releases'))
    if (-not $release.StartsWith($releasesRoot + [System.IO.Path]::DirectorySeparatorChar)) {
        throw "Release path escapes deployment root"
    }
    return $release
}

function Assert-FrontendArtifact {
    param([Parameter(Mandatory = $true)][string]$Directory, [Parameter(Mandatory = $true)]$Manifest)
    # 前端目录与清单必须逐文件一致，避免只校验 JAR 却发布了被替换的页面资源。
    $root = [System.IO.Path]::GetFullPath($Directory)
    if (-not (Test-Path -LiteralPath $root -PathType Container)) { throw 'Frontend artifact directory is missing' }
    $expected = @($Manifest.frontendFiles)
    if ($expected.Count -eq 0) { throw 'Frontend file digest manifest is empty' }
    $seen = @{}
    foreach ($entry in $expected) {
        $name = [string]$entry.path
        if ($name -notmatch '^[A-Za-z0-9._/-]+$' -or $name.StartsWith('/') -or
            @($name.Split('/') | Where-Object { $_ -eq '.' -or $_ -eq '..' -or $_ -eq '' }).Count -gt 0 -or
            $seen.ContainsKey($name)) { throw "Invalid or duplicate frontend file path: $name" }
        $seen[$name] = $true
        $file = Join-Path $root ($name.Replace('/', [System.IO.Path]::DirectorySeparatorChar))
        if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw "Frontend file is missing: $name" }
        $actual = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actual -ne [string]$entry.sha256) { throw "Frontend file SHA-256 mismatch: $name" }
    }
    $actualCount = @(Get-ChildItem -LiteralPath $root -File -Recurse).Count
    if ($actualCount -ne $expected.Count) { throw 'Frontend file count differs from release manifest' }
}

function Assert-ReleaseFrontend {
    param([Parameter(Mandatory = $true)][string]$Directory, [Parameter(Mandatory = $true)]$Manifest)
    # 旧版清单没有前端逐文件摘要，仍允许重启和回滚；新版清单必须完整校验。
    if (@($Manifest.PSObject.Properties.Name) -contains 'frontendFiles') {
        Assert-FrontendArtifact -Directory $Directory -Manifest $Manifest
    } elseif ([int]$Manifest.schemaVersion -ge 2) {
        throw 'Release manifest v2 requires frontend file digests'
    } else {
        Write-Warning 'Legacy release manifest has no frontend digests; only its backend JAR digest can be checked'
    }
}

function Assert-FrontendServer {
    param([Parameter(Mandatory = $true)][string]$ReleaseDirectory, [Parameter(Mandatory = $true)]$Manifest)
    $file = Join-Path $ReleaseDirectory 'scripts/frontend-server.mjs'
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw 'Packaged frontend server is missing' }
    if ([int]$Manifest.schemaVersion -ge 3) {
        $expected = [string]$Manifest.frontendServerSha256
        if ($expected -notmatch '^[a-f0-9]{64}$' -or
                (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected) {
            throw 'Packaged frontend server SHA-256 mismatch'
        }
    }
}

function Write-Utf8NoBom {
    param([Parameter(Mandatory = $true)][string]$Path, [Parameter(Mandatory = $true)][string]$Value)
    [System.IO.File]::WriteAllText([System.IO.Path]::GetFullPath($Path), $Value, (New-Object System.Text.UTF8Encoding($false)))
}

function Wait-DeploymentEndpoint {
    param(
        [Parameter(Mandatory = $true)][string]$Uri,
        [int]$TimeoutSeconds = 60
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $lastError = $null
    do {
        try {
            $response = Invoke-WebRequest -Uri $Uri -UseBasicParsing -TimeoutSec 10
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 400) { return $response }
            $lastError = "HTTP $($response.StatusCode)"
        } catch {
            $lastError = $_.Exception.Message
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "Endpoint did not become ready within $TimeoutSeconds seconds: $Uri; last error: $lastError"
}

function Switch-DeploymentStatic {
    param(
        [Parameter(Mandatory = $true)][string]$Root,
        [Parameter(Mandatory = $true)][string]$ReleaseDirectory,
        [Parameter(Mandatory = $true)][string]$EvidenceLabel
    )
    $source = Join-Path $ReleaseDirectory 'frontend'
    if (-not (Test-Path -LiteralPath $source -PathType Container)) { throw "Frontend package is missing: $source" }
    $stage = Join-Path $Root ('.www-stage-' + [guid]::NewGuid().ToString('N'))
    $www = Join-Path $Root 'www'
    $backupRoot = Join-Path $Root 'backups'
    New-Item -ItemType Directory -Force -Path $backupRoot | Out-Null
    Copy-Item -LiteralPath $source -Destination $stage -Recurse
    if (Test-Path -LiteralPath $www -PathType Container) {
        $backup = Join-Path $backupRoot ("www-$EvidenceLabel-$((Get-Date).ToString('yyyyMMddHHmmssfff'))")
        Move-Item -LiteralPath $www -Destination $backup
    }
    Move-Item -LiteralPath $stage -Destination $www
}
