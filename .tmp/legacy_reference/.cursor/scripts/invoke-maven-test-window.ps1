param(
    [Parameter(Mandatory = $true)]
    [string]$Owner,
    [ValidateRange(0, 300)]
    [int]$WaitSeconds = 0,
    [string]$JavaHome = $env:EXAMINE_JAVA21_HOME,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$MavenArgs
)

$ErrorActionPreference = 'Stop'
if (-not $MavenArgs -or $MavenArgs.Count -eq 0) {
    throw 'Maven arguments are required.'
}

$workspace = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$lockPath = Join-Path $workspace '.cursor\session\maven-test-window.lock'
$deadline = [DateTime]::UtcNow.AddSeconds($WaitSeconds)
$stream = $null

function Get-JavaVersionText {
    param([Parameter(Mandatory = $true)][string]$Executable)

    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $Executable
    $startInfo.Arguments = '-version'
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $process = [System.Diagnostics.Process]::Start($startInfo)
    $standardOutput = $process.StandardOutput.ReadToEnd()
    $standardError = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    return "$standardOutput`n$standardError"
}

if ([string]::IsNullOrWhiteSpace($JavaHome)) {
    $javaExecutables = @(& where.exe java 2>$null)
    foreach ($javaExecutable in $javaExecutables) {
        $versionText = Get-JavaVersionText -Executable $javaExecutable
        if ($versionText -match '(?m)^(?:openjdk |java )?version "21(?:\.|\")') {
            $JavaHome = Split-Path -Parent (Split-Path -Parent $javaExecutable)
            break
        }
    }
}
$javaHomeMissing = [string]::IsNullOrWhiteSpace($JavaHome)
$javaExecutableMissing = $true
if (-not $javaHomeMissing) {
    $javaExecutableMissing = -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\java.exe') -PathType Leaf)
}
if ($javaHomeMissing -or $javaExecutableMissing) {
    throw 'A Java 21 home is required. Set EXAMINE_JAVA21_HOME or pass -JavaHome.'
}
$resolvedJavaHome = (Resolve-Path -LiteralPath $JavaHome).Path
$javaVersion = Get-JavaVersionText -Executable (Join-Path $resolvedJavaHome 'bin\java.exe')
if ($javaVersion -notmatch '(?m)^(?:openjdk |java )?version "21(?:\.|\")') {
    throw "Maven test windows require Java 21; resolved $resolvedJavaHome is not Java 21."
}

do {
    try {
        $stream = [System.IO.File]::Open(
            $lockPath,
            [System.IO.FileMode]::OpenOrCreate,
            [System.IO.FileAccess]::ReadWrite,
            [System.IO.FileShare]::None)
        break
    }
    catch [System.IO.IOException] {
        if ([DateTime]::UtcNow -ge $deadline) {
            throw "Maven test window is already owned; $Owner must retry after the active module finishes."
        }
        Start-Sleep -Milliseconds 500
    }
} while ($true)

try {
    $lease = [ordered]@{
        owner = $Owner
        processId = $PID
        acquiredAt = [DateTimeOffset]::Now.ToString('o')
        command = @('mvn.cmd') + $MavenArgs
    } | ConvertTo-Json -Depth 4
    $bytes = [System.Text.UTF8Encoding]::new($false).GetBytes($lease)
    $stream.SetLength(0)
    $stream.Write($bytes, 0, $bytes.Length)
    $stream.Flush($true)

    Push-Location $workspace
    try {
        $env:JAVA_HOME = $resolvedJavaHome
        $env:Path = "$(Join-Path $resolvedJavaHome 'bin');$env:Path"
        & mvn.cmd @MavenArgs
        $exitCode = $LASTEXITCODE
    }
    finally {
        Pop-Location
    }
    if ($exitCode -ne 0) {
        throw "Maven test window for $Owner failed with exit code $exitCode."
    }
    Write-Output "Maven test window completed for $Owner."
}
finally {
    if ($null -ne $stream) {
        $stream.Dispose()
    }
}
