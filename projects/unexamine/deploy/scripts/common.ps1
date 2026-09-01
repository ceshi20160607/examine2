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
