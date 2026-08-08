Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:DeliveryPackageRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$script:DeliveryComposePath = Join-Path $script:DeliveryPackageRoot 'deploy\compose.yaml'
$script:DeliveryEnvPath = Join-Path $script:DeliveryPackageRoot '.env'

function Get-DeliveryEnvMap {
    param([string]$Path = $script:DeliveryEnvPath)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Missing $Path. Copy .env.example to .env and replace every CHANGE_ME value."
    }
    $values = @{}
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith('#')) { continue }
        $separator = $trimmed.IndexOf('=')
        if ($separator -le 0) { throw "Invalid environment line: $line" }
        $key = $trimmed.Substring(0, $separator).Trim()
        $value = $trimmed.Substring($separator + 1).Trim()
        $values[$key] = $value
    }
    return $values
}

function Assert-DeliveryEnvironment {
    param([hashtable]$Values)

    $required = @(
        'EXAMINE_DB_NAME',
        'EXAMINE_DB_USERNAME',
        'EXAMINE_DB_PASSWORD',
        'EXAMINE_DB_ROOT_PASSWORD',
        'EXAMINE_REDIS_PASSWORD',
        'EXAMINE_DATA_ROOT',
        'EXAMINE_BOOTSTRAP_ROOT_USERNAME',
        'EXAMINE_BOOTSTRAP_ROOT_PASSWORD'
    )
    foreach ($key in $required) {
        if (-not $Values.ContainsKey($key) -or [string]::IsNullOrWhiteSpace([string]$Values[$key])) {
            throw "Required environment value is missing: $key"
        }
        if ([string]$Values[$key] -match 'CHANGE_ME|PLACEHOLDER|EXAMPLE') {
            throw "Replace the placeholder value for $key before startup."
        }
    }
    foreach ($key in @('EXAMINE_DB_PASSWORD', 'EXAMINE_DB_ROOT_PASSWORD', 'EXAMINE_REDIS_PASSWORD', 'EXAMINE_BOOTSTRAP_ROOT_PASSWORD')) {
        if ([string]$Values[$key].Length -lt 16) {
            throw "$key must contain at least 16 characters."
        }
    }
    if (-not [System.IO.Path]::IsPathRooted([string]$Values.EXAMINE_DATA_ROOT)) {
        throw 'EXAMINE_DATA_ROOT must be an absolute durable path shared by every application version.'
    }
}

function Assert-DockerCompose {
    & docker compose version *> $null
    if ($LASTEXITCODE -ne 0) { throw 'Docker Compose v2 is required.' }
    if (-not (Test-Path -LiteralPath $script:DeliveryComposePath -PathType Leaf)) {
        throw "Compose file is missing: $script:DeliveryComposePath"
    }
}

function Invoke-DeliveryCompose {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments,
        [string]$ArtifactRoot = $script:DeliveryPackageRoot
    )

    $resolvedArtifact = (Resolve-Path -LiteralPath $ArtifactRoot).Path
    $previousArtifact = $env:EXAMINE_ARTIFACT_ROOT
    try {
        $env:EXAMINE_ARTIFACT_ROOT = $resolvedArtifact.Replace('\', '/')
        & docker compose --project-name examine2 --project-directory $script:DeliveryPackageRoot `
            --env-file $script:DeliveryEnvPath -f $script:DeliveryComposePath @Arguments
        if ($LASTEXITCODE -ne 0) {
            throw "docker compose failed: $($Arguments -join ' ')"
        }
    } finally {
        $env:EXAMINE_ARTIFACT_ROOT = $previousArtifact
    }
}

function Get-DeliveryPort {
    param([hashtable]$Values, [string]$Key, [int]$Default)
    if (-not $Values.ContainsKey($Key) -or [string]::IsNullOrWhiteSpace([string]$Values[$Key])) {
        return $Default
    }
    $port = 0
    if (-not [int]::TryParse([string]$Values[$Key], [ref]$port) -or $port -lt 1 -or $port -gt 65535) {
        throw "Invalid TCP port in $Key."
    }
    return $port
}

function Wait-DeliveryHealth {
    param([hashtable]$Values, [int]$TimeoutSeconds = 240)

    $backendPort = Get-DeliveryPort $Values 'EXAMINE_BACKEND_PORT' 8080
    $webPort = Get-DeliveryPort $Values 'EXAMINE_WEB_PORT' 8088
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try {
            $health = Invoke-RestMethod -Uri "http://127.0.0.1:$backendPort/management/health" -TimeoutSec 4
            $web = Invoke-WebRequest -Uri "http://127.0.0.1:$webPort/" -Method Head -TimeoutSec 4 -UseBasicParsing
            if ($health.status -eq 'UP' -and $web.StatusCode -eq 200) {
                return [pscustomobject]@{
                    backend = "http://127.0.0.1:$backendPort/management/health"
                    frontend = "http://127.0.0.1:$webPort/"
                    status = 'UP'
                }
            }
        } catch {
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "Examine2 did not become healthy within $TimeoutSeconds seconds."
}

function Assert-PathInsidePackage {
    param([Parameter(Mandatory = $true)][string]$Path)
    $full = [System.IO.Path]::GetFullPath($Path)
    $prefix = $script:DeliveryPackageRoot.TrimEnd('\') + '\'
    if (-not $full.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Path must stay inside the package root: $full"
    }
    return $full
}

function Get-DeliveryDataRoot {
    param([hashtable]$Values)
    $full = [System.IO.Path]::GetFullPath([string]$Values.EXAMINE_DATA_ROOT).TrimEnd('\')
    if ($full -eq [System.IO.Path]::GetPathRoot($full)) { throw 'EXAMINE_DATA_ROOT cannot be a filesystem root.' }
    return $full
}

function Assert-PathInsideDataRoot {
    param([Parameter(Mandatory = $true)][string]$Path, [hashtable]$Values)
    $root = Get-DeliveryDataRoot $Values
    $full = [System.IO.Path]::GetFullPath($Path)
    $prefix = $root + '\'
    if (-not $full.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Path must stay inside the configured data root: $full"
    }
    return $full
}

function Invoke-NativeWithInputFile {
    param(
        [Parameter(Mandatory = $true)][string]$Executable,
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [Parameter(Mandatory = $true)][string]$InputFile
    )
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $Executable
    $startInfo.Arguments = (($Arguments | ForEach-Object { ConvertTo-NativeArgument ([string]$_) }) -join ' ')
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $process = [System.Diagnostics.Process]::Start($startInfo)
    try {
        $reader = [System.IO.File]::OpenText($InputFile)
        try {
            $buffer = New-Object char[] 8192
            while (($count = $reader.Read($buffer, 0, $buffer.Length)) -gt 0) {
                $process.StandardInput.Write($buffer, 0, $count)
            }
        } finally {
            $reader.Dispose()
            $process.StandardInput.Close()
        }
        $stdout = $process.StandardOutput.ReadToEnd()
        $stderr = $process.StandardError.ReadToEnd()
        $process.WaitForExit()
        if ($process.ExitCode -ne 0) { throw "$Executable failed: $stderr" }
        return $stdout
    } finally {
        $process.Dispose()
    }
}

function ConvertTo-NativeArgument {
    param([AllowEmptyString()][string]$Value)
    if ($Value.Length -gt 0 -and $Value -notmatch '[\s"]') { return $Value }
    $builder = New-Object System.Text.StringBuilder
    [void]$builder.Append('"')
    $slashes = 0
    foreach ($character in $Value.ToCharArray()) {
        if ($character -eq '\') { $slashes++; continue }
        if ($character -eq '"') {
            [void]$builder.Append(('\' * ($slashes * 2 + 1)))
            [void]$builder.Append('"')
        } else {
            if ($slashes -gt 0) { [void]$builder.Append(('\' * $slashes)) }
            [void]$builder.Append($character)
        }
        $slashes = 0
    }
    if ($slashes -gt 0) { [void]$builder.Append(('\' * ($slashes * 2))) }
    [void]$builder.Append('"')
    return $builder.ToString()
}
