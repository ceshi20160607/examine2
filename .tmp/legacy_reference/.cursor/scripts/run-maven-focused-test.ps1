param(
    [Parameter(Mandatory = $true)]
    [string]$Module,
    [Parameter(Mandatory = $true)]
    [string]$Tests,
    [string]$Pom = 'backend/pom.xml',
    [ValidateRange(0, 3600)]
    [int]$WaitSeconds = 600
)

$ErrorActionPreference = 'Stop'
$baseRoot = Split-Path -Parent $PSScriptRoot
$repositoryRoot = Split-Path -Parent $baseRoot

function Resolve-RepositoryFile([string]$Value) {
    $candidate = if ([System.IO.Path]::IsPathRooted($Value)) {
        [System.IO.Path]::GetFullPath($Value)
    } else {
        [System.IO.Path]::GetFullPath((Join-Path $repositoryRoot $Value))
    }
    $prefix = $repositoryRoot.TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar
    if (-not $candidate.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Maven pom is outside the repository: $Value"
    }
    if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) {
        throw "Maven pom does not exist: $Value"
    }
    return $candidate
}

function Get-JavaVersionOutput {
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $process.StartInfo.FileName = 'java.exe'
    $process.StartInfo.Arguments = '-version'
    $process.StartInfo.WorkingDirectory = $repositoryRoot
    $process.StartInfo.UseShellExecute = $false
    $process.StartInfo.RedirectStandardOutput = $true
    $process.StartInfo.RedirectStandardError = $true
    $process.StartInfo.CreateNoWindow = $true
    [void]$process.Start()
    $stdout = $process.StandardOutput.ReadToEnd()
    $stderr = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    return [ordered]@{
        exitCode = $process.ExitCode
        output = ($stdout + $stderr).Trim()
    }
}

if ($Module -notmatch '^[A-Za-z0-9_.-]+$') {
    throw "Invalid Maven module: $Module"
}
if ([string]::IsNullOrWhiteSpace($Tests) -or $Tests -notmatch '^[A-Za-z0-9_.$,#*?-]+$') {
    throw "Invalid focused-test selector: $Tests"
}

$pomPath = Resolve-RepositoryFile $Pom
$javaVersion = Get-JavaVersionOutput
if ($javaVersion.exitCode -ne 0 -or $javaVersion.output -notmatch 'version\s+"21(?:\.|\")') {
    throw "Java 21 is required. Current java -version output: $($javaVersion.output)"
}

$lockPath = Join-Path $repositoryRoot '.cursor\session\maven-test-window.lock'
$deadline = [DateTimeOffset]::UtcNow.AddSeconds($WaitSeconds)
$lockStream = $null
do {
    try {
        $lockStream = [System.IO.File]::Open(
                $lockPath,
                [System.IO.FileMode]::OpenOrCreate,
                [System.IO.FileAccess]::ReadWrite,
                [System.IO.FileShare]::None)
        break
    } catch [System.IO.IOException] {
        if ([DateTimeOffset]::UtcNow -ge $deadline) {
            throw "Maven test window is busy; focused test for $Module timed out waiting for the shared target lock."
        }
        Start-Sleep -Milliseconds 250
    }
} while ($true)

try {
    $lease = [ordered]@{
        owner = 'run-maven-focused-test'
        processId = $PID
        module = $Module
        tests = $Tests
        acquiredAt = [DateTimeOffset]::Now.ToString('o')
    } | ConvertTo-Json -Depth 4
    $leaseBytes = [System.Text.UTF8Encoding]::new($false).GetBytes($lease)
    $lockStream.SetLength(0)
    $lockStream.Write($leaseBytes, 0, $leaseBytes.Length)
    $lockStream.Flush($true)

    & mvn -f $pomPath -pl $Module -am -DskipTests clean install
    if ($LASTEXITCODE -ne 0) {
        throw "Maven reactor build failed for module $Module."
    }

    & mvn -f $pomPath -pl $Module "-Dtest=$Tests" test
    if ($LASTEXITCODE -ne 0) {
        throw "Focused Maven tests failed for module ${Module}: $Tests"
    }
} finally {
    if ($null -ne $lockStream) {
        $lockStream.Dispose()
    }
}

Write-Output "MAVEN_FOCUSED_TEST_PASS module=$Module tests=$Tests"
