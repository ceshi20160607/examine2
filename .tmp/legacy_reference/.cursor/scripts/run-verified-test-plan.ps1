param(
    [Parameter(Mandatory = $true)]
    [string]$Plan,
    [Parameter(Mandatory = $true)]
    [string]$EvidenceRoot,
    [ValidateRange(1, 240)]
    [int]$CommandTimeoutMinutes = 120,
    [switch]$ValidateOnly
)

$ErrorActionPreference = 'Stop'
$scriptRoot = Split-Path -Parent $PSScriptRoot
$repositoryRoot = Split-Path -Parent $scriptRoot
$validatorPath = Join-Path $scriptRoot 'skills\verify-by-delivery-level\scripts\validate_test_plan.py'

function Resolve-RepositoryPath([string]$Value, [string]$Label, [bool]$MustExist) {
    if ([string]::IsNullOrWhiteSpace($Value)) {
        throw "$Label is empty."
    }
    $candidate = if ([System.IO.Path]::IsPathRooted($Value)) {
        [System.IO.Path]::GetFullPath($Value)
    } else {
        [System.IO.Path]::GetFullPath((Join-Path $repositoryRoot $Value))
    }
    $prefix = $repositoryRoot.TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar
    if (-not $candidate.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "$Label is outside the repository: $Value"
    }
    if ($MustExist -and -not (Test-Path -LiteralPath $candidate -PathType Leaf)) {
        throw "$Label does not exist: $Value"
    }
    return $candidate
}

function Get-PortableRelativePath([string]$Path) {
    $separator = [System.IO.Path]::DirectorySeparatorChar
    $fromUri = [Uri]::new($repositoryRoot.TrimEnd($separator) + $separator)
    $toUri = [Uri]::new($Path)
    return [Uri]::UnescapeDataString($fromUri.MakeRelativeUri($toUri).ToString()).Replace('\', '/')
}

function Get-Sha256([string]$Path) {
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Get-TextSha256([string]$Value) {
    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($Value)
        $hash = $sha256.ComputeHash($bytes)
        return ([System.BitConverter]::ToString($hash)).Replace('-', '').ToLowerInvariant()
    } finally {
        $sha256.Dispose()
    }
}

function Get-TestCount([string]$Output) {
    $counts = [System.Collections.Generic.List[int]]::new()
    foreach ($match in [regex]::Matches($Output, '(?im)Tests run:\s*(\d+)')) {
        $counts.Add([int]$match.Groups[1].Value)
    }
    foreach ($match in [regex]::Matches($Output, '(?im)(?:Tests?|test files?)\s*[:=]?\s*(\d+)\s+passed')) {
        $counts.Add([int]$match.Groups[1].Value)
    }
    foreach ($match in [regex]::Matches($Output, '(?im)^\s*(\d+)\s+passed(?:\s|$)')) {
        $counts.Add([int]$match.Groups[1].Value)
    }
    if ($counts.Count -eq 0) {
        return 0
    }
    return ($counts | Measure-Object -Maximum).Maximum
}

function Get-MavenModule([string]$Command) {
    $match = [regex]::Match($Command, '(?i)(?:-Module|-pl)\s+(?:"([^"]+)"|''([^'']+)''|([A-Za-z0-9_.-]+))')
    if (-not $match.Success) {
        return $null
    }
    foreach ($group in @($match.Groups[1], $match.Groups[2], $match.Groups[3])) {
        if ($group.Success -and -not [string]::IsNullOrWhiteSpace($group.Value)) {
            return $group.Value
        }
    }
    return $null
}

function Get-MavenSelectors([string]$Command) {
    $match = [regex]::Match($Command, '(?i)(?:-Tests\s+|-Dtest=)(?:"([^"]+)"|''([^'']+)''|([^\s]+))')
    if (-not $match.Success) {
        return @()
    }
    $value = @($match.Groups[1], $match.Groups[2], $match.Groups[3]) |
            Where-Object { $_.Success -and -not [string]::IsNullOrWhiteSpace($_.Value) } |
            Select-Object -First 1 -ExpandProperty Value
    $value = $value.Trim([char[]](34, 39))
    return @($value.Split(',') | ForEach-Object { $_.Trim() } | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
}

function Get-SurefireFiles([string]$Module) {
    if (-not [string]::IsNullOrWhiteSpace($Module)) {
        $reportRoot = Join-Path $repositoryRoot ("backend\{0}\target\surefire-reports" -f $Module)
        if (-not (Test-Path -LiteralPath $reportRoot -PathType Container)) {
            return @()
        }
        return @(Get-ChildItem -LiteralPath $reportRoot -File -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue)
    }
    return @(Get-ChildItem -LiteralPath $repositoryRoot -Recurse -File -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -match '[\\/]target[\\/]surefire-reports[\\/]' })
}

function Get-SurefireSnapshot([string]$Module) {
    $snapshot = @{}
    foreach ($report in @(Get-SurefireFiles $Module)) {
        $snapshot[$report.FullName] = [long]$report.LastWriteTimeUtc.Ticks
    }
    return $snapshot
}

function Get-WildcardRegex([string]$Value, [bool]$AllowParameterizedSuffix) {
    $pattern = [regex]::Escape($Value).Replace('\*', '.*').Replace('\?', '.')
    if ($AllowParameterizedSuffix -and $Value -notmatch '[*?]') {
        return '^' + $pattern + '(?:\(|\[|$)'
    }
    return '^' + $pattern + '$'
}

function Test-SuiteMatchesSelector([object]$Report, [string]$Selector) {
    $parts = $Selector.Split('#', 2)
    $classSelector = $parts[0]
    $methodSelector = if ($parts.Count -eq 2) { $parts[1] } else { $null }
    $compareFullClassName = $classSelector.Contains('.')
    $classPattern = Get-WildcardRegex $classSelector $false
    $methodPattern = if ([string]::IsNullOrWhiteSpace($methodSelector)) { $null } else { Get-WildcardRegex $methodSelector $true }
    return @($Report.testCases | Where-Object {
        $caseClass = if ($compareFullClassName) { [string]$_.className } else { ([string]$_.className).Split('.')[-1] }
        -not [bool]$_.skipped -and
                $caseClass -cmatch $classPattern -and
                ($null -eq $methodPattern -or ([string]$_.name) -cmatch $methodPattern)
    }).Count -gt 0
}

function Get-FreshSurefireResult([hashtable]$Before, [string]$Module, [string[]]$Selectors) {
    $reports = [System.Collections.Generic.List[object]]::new()
    $executed = 0
    $reported = 0
    $skipped = 0
    $failures = 0
    $errors = 0
    foreach ($report in @(Get-SurefireFiles $Module)) {
        $previousTicks = if ($Before.ContainsKey($report.FullName)) { [long]$Before[$report.FullName] } else { -1 }
        if ($previousTicks -eq [long]$report.LastWriteTimeUtc.Ticks) {
            continue
        }
        try {
            [xml]$document = Get-Content -LiteralPath $report.FullName -Raw -Encoding UTF8
            $suites = @($document.SelectNodes('//testsuite'))
            if ($suites.Count -eq 0) {
                continue
            }
            $fileReported = 0
            $fileSkipped = 0
            $fileFailures = 0
            $fileErrors = 0
            $fileSuiteNames = [System.Collections.Generic.List[string]]::new()
            $fileTestCases = [System.Collections.Generic.List[object]]::new()
            foreach ($suite in $suites) {
                $fileReported += [int]$suite.tests
                $fileSkipped += [int]$suite.skipped
                $fileFailures += [int]$suite.failures
                $fileErrors += [int]$suite.errors
                if (-not [string]::IsNullOrWhiteSpace([string]$suite.name)) {
                    $fileSuiteNames.Add([string]$suite.name)
                }
                foreach ($testCase in @($suite.SelectNodes('.//testcase'))) {
                    $fileTestCases.Add([ordered]@{
                        className = [string]$testCase.classname
                        name = [string]$testCase.name
                        skipped = $null -ne $testCase.SelectSingleNode('./skipped')
                    })
                }
            }
            $reported += $fileReported
            $skipped += $fileSkipped
            $failures += $fileFailures
            $errors += $fileErrors
            $executed += [Math]::Max(0, $fileReported - $fileSkipped)
            $reports.Add([ordered]@{
                fullPath = $report.FullName
                path = Get-PortableRelativePath $report.FullName
                sha256 = Get-Sha256 $report.FullName
                tests = $fileReported
                skipped = $fileSkipped
                failures = $fileFailures
                errors = $fileErrors
                suiteNames = @($fileSuiteNames)
                testCases = @($fileTestCases)
            })
        } catch {
            throw "Invalid Surefire XML report $($report.FullName): $($_.Exception.Message)"
        }
    }
    $missingSelectors = [System.Collections.Generic.List[string]]::new()
    foreach ($selector in $Selectors) {
        if (-not @($reports | Where-Object { Test-SuiteMatchesSelector $_ $selector }).Count) {
            $missingSelectors.Add($selector)
        }
    }
    return [ordered]@{
        reportCount = $reports.Count
        executedTestCount = $executed
        reportedTestCount = $reported
        skippedTestCount = $skipped
        failureCount = $failures
        errorCount = $errors
        missingSelectors = $missingSelectors
        reports = @($reports)
    }
}

function Invoke-CapturedCommand([string]$Command, [string]$LogPath, [int]$TimeoutMinutes) {
    $start = [DateTimeOffset]::Now
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $process.StartInfo.FileName = 'cmd.exe'
    $process.StartInfo.Arguments = "/d /s /c `"$Command`""
    $process.StartInfo.WorkingDirectory = $repositoryRoot
    $process.StartInfo.UseShellExecute = $false
    $process.StartInfo.RedirectStandardOutput = $true
    $process.StartInfo.RedirectStandardError = $true
    $process.StartInfo.CreateNoWindow = $true
    [void]$process.Start()
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    $timeoutMilliseconds = [int]([TimeSpan]::FromMinutes($TimeoutMinutes).TotalMilliseconds)
    $finished = $process.WaitForExit($timeoutMilliseconds)
    if (-not $finished) {
        & taskkill.exe /PID $process.Id /T /F *> $null
        $terminated = $process.WaitForExit(10000)
        if (-not $terminated) {
            try {
                $process.Kill()
            } catch {
                # The process may have exited between the bounded wait and Kill.
            }
            $terminated = $process.WaitForExit(5000)
        }
        if (-not $terminated) {
            throw "Timed-out command process tree did not terminate: $Command"
        }
    }
    $readTasks = [System.Threading.Tasks.Task[]]@($stdoutTask, $stderrTask)
    $readersCompleted = [System.Threading.Tasks.Task]::WaitAll($readTasks, 10000)
    if ($readersCompleted) {
        $stdout = $stdoutTask.GetAwaiter().GetResult()
        $stderr = $stderrTask.GetAwaiter().GetResult()
    } else {
        $stdout = ''
        $stderr = 'COMMAND_CAPTURE_TIMEOUT: stdout/stderr handles did not close after process termination.'
        $finished = $false
    }
    $combined = $stdout
    if (-not [string]::IsNullOrEmpty($stderr)) {
        $combined += if ($combined.EndsWith("`n")) { '' } else { "`r`n" }
        $combined += $stderr
    }
    [System.IO.File]::WriteAllText($LogPath, $combined, [System.Text.UTF8Encoding]::new($false))
    return [ordered]@{
        exitCode = if ($finished) { $process.ExitCode } else { -1 }
        timedOut = -not $finished
        startedAt = $start.ToString('o')
        completedAt = [DateTimeOffset]::Now.ToString('o')
        output = $combined
    }
}

function Invoke-ReadOnlyProcess([string]$FileName, [string]$Arguments) {
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $process.StartInfo.FileName = $FileName
    $process.StartInfo.Arguments = $Arguments
    $process.StartInfo.WorkingDirectory = $repositoryRoot
    $process.StartInfo.UseShellExecute = $false
    $process.StartInfo.RedirectStandardOutput = $true
    $process.StartInfo.RedirectStandardError = $true
    $process.StartInfo.CreateNoWindow = $true
    [void]$process.Start()
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    $process.WaitForExit()
    return [ordered]@{
        exitCode = $process.ExitCode
        stdout = $stdoutTask.GetAwaiter().GetResult()
        stderr = $stderrTask.GetAwaiter().GetResult()
    }
}

$planPath = Resolve-RepositoryPath $Plan 'Test plan' $true
if (-not (Test-Path -LiteralPath $validatorPath -PathType Leaf)) {
    throw "Test-plan validator is missing: $validatorPath"
}

& python $validatorPath $planPath
if ($LASTEXITCODE -ne 0) {
    throw "Test plan validation failed with exit code $LASTEXITCODE."
}

$planValue = Get-Content -Raw -Encoding UTF8 $planPath | ConvertFrom-Json
$expectedEvidenceRelative = ".cursor/session/evidence/baseline/$($planValue.scopeId)"
$evidencePath = Resolve-RepositoryPath $EvidenceRoot 'Evidence root' $false
$expectedEvidencePath = Resolve-RepositoryPath $expectedEvidenceRelative 'Expected evidence root' $false
if ($evidencePath -ne $expectedEvidencePath) {
    throw "Evidence root must be exactly $expectedEvidenceRelative for scope $($planValue.scopeId)."
}

$commands = @($planValue.tests | ForEach-Object { [string]$_.command })
if ($commands.Count -eq 0 -or @($commands | Sort-Object -Unique).Count -ne $commands.Count) {
    throw 'The test plan must contain at least one unique command.'
}
foreach ($command in $commands) {
    if ([string]::IsNullOrWhiteSpace($command)) {
        throw 'A test command is empty.'
    }
    if ($command -match '(?i)failIfNoSpecifiedTests\s*=\s*false') {
        throw 'failIfNoSpecifiedTests=false is forbidden.'
    }
    if ($command -match '(?i)(performance|benchmark|load[-_ ]?test|stress|million|million-record|k6|jmeter|gatling)') {
        throw 'Performance or capacity commands are forbidden in the functional cycle runner.'
    }
}

if ($ValidateOnly) {
    Write-Output "VERIFIED_TEST_PLAN_READY $($planValue.scopeId) ($($commands.Count) commands)"
    exit 0
}

[void](New-Item -ItemType Directory -Path $evidencePath -Force)
$escapedRepositoryRoot = $repositoryRoot.Replace('"', '\"')
$headResult = Invoke-ReadOnlyProcess 'git.exe' "-C `"$escapedRepositoryRoot`" rev-parse HEAD"
$sourceSha = $headResult.stdout.Trim()
if ($headResult.exitCode -ne 0 -or [string]::IsNullOrWhiteSpace([string]$sourceSha)) {
    $sourceSha = 'unversioned'
}
$sourceDirty = $false
if ($sourceSha -ne 'unversioned') {
    $statusResult = Invoke-ReadOnlyProcess 'git.exe' "-C `"$escapedRepositoryRoot`" status --porcelain --untracked-files=normal"
    $sourceDirty = $statusResult.exitCode -ne 0 -or -not [string]::IsNullOrWhiteSpace($statusResult.stdout)
}

$records = [System.Collections.Generic.List[object]]::new()
$overallPassed = $true
for ($index = 0; $index -lt @($planValue.tests).Count; $index++) {
    $test = @($planValue.tests)[$index]
    $safeId = ([string]$test.id) -replace '[^A-Za-z0-9._-]', '_'
    $logPath = Join-Path $evidencePath ("{0:D2}-{1}.log" -f ($index + 1), $safeId)
    $isMavenCommand = ([string]$test.command) -match '(?i)(mvn(?:\.cmd)?|run-maven-focused-test\.ps1)'
    $mavenModule = if ($isMavenCommand) { Get-MavenModule ([string]$test.command) } else { $null }
    $mavenSelectors = if ($isMavenCommand) { @(Get-MavenSelectors ([string]$test.command)) } else { @() }
    if ($isMavenCommand -and ([string]::IsNullOrWhiteSpace($mavenModule) -or $mavenSelectors.Count -eq 0)) {
        throw "Maven evidence commands must declare one target module and at least one focused test selector: $($test.command)"
    }
    $surefireBefore = if ($isMavenCommand) { Get-SurefireSnapshot $mavenModule } else { @{} }
    $execution = Invoke-CapturedCommand ([string]$test.command) $logPath $CommandTimeoutMinutes
    $surefire = if ($isMavenCommand) { Get-FreshSurefireResult $surefireBefore $mavenModule $mavenSelectors } else { $null }
    $testCount = if ($isMavenCommand) { [int]$surefire.executedTestCount } else { Get-TestCount ([string]$execution.output) }
    $passed = -not $execution.timedOut -and $execution.exitCode -eq 0 -and $testCount -gt 0
    if ($isMavenCommand) {
        $passed = $passed -and $surefire.reportCount -gt 0 -and $surefire.failureCount -eq 0 -and $surefire.errorCount -eq 0 -and @($surefire.missingSelectors).Count -eq 0
    }
    if (-not $passed) {
        $overallPassed = $false
    }
    [string[]]$missingTestSelectorValues = @()
    if ($isMavenCommand) {
        $missingTestSelectorValues = [string[]]@($surefire.missingSelectors)
    }
    $record = [ordered]@{
        testId = [string]$test.id
        category = [string]$test.category
        outcomeClass = [string]$test.outcomeClass
        command = [string]$test.command
        commandSha256 = Get-TextSha256 ([string]$test.command)
        exitCode = [int]$execution.exitCode
        timedOut = [bool]$execution.timedOut
        mavenModule = $mavenModule
        expectedTestSelectors = @($mavenSelectors)
        missingTestSelectors = $missingTestSelectorValues
        executedTestCount = [int]$testCount
        reportedTestCount = if ($isMavenCommand) { [int]$surefire.reportedTestCount } else { [int]$testCount }
        skippedTestCount = if ($isMavenCommand) { [int]$surefire.skippedTestCount } else { 0 }
        failureCount = if ($isMavenCommand) { [int]$surefire.failureCount } else { 0 }
        errorCount = if ($isMavenCommand) { [int]$surefire.errorCount } else { 0 }
        reportPaths = @()
        reportSha256 = @()
        startedAt = [string]$execution.startedAt
        completedAt = [string]$execution.completedAt
        verdict = if ($passed) { 'pass' } else { 'fail' }
        logPath = Get-PortableRelativePath $logPath
        logSha256 = Get-Sha256 $logPath
    }
    if ($isMavenCommand) {
        $reportEvidenceRoot = Join-Path $evidencePath 'surefire-reports'
        [void](New-Item -ItemType Directory -Path $reportEvidenceRoot -Force)
        foreach ($report in @($surefire.reports)) {
            $reportFileName = "{0:D2}-{1}-{2}" -f ($index + 1), $safeId, ([System.IO.Path]::GetFileName([string]$report.fullPath))
            $reportEvidencePath = Join-Path $reportEvidenceRoot $reportFileName
            Copy-Item -LiteralPath ([string]$report.fullPath) -Destination $reportEvidencePath -Force
            $record.reportPaths += Get-PortableRelativePath $reportEvidencePath
            $record.reportSha256 += Get-Sha256 $reportEvidencePath
        }
    }
    $recordPath = Join-Path $evidencePath ("{0:D2}-{1}.json" -f ($index + 1), $safeId)
    [System.IO.File]::WriteAllText($recordPath, (($record | ConvertTo-Json -Depth 8) + "`n"), [System.Text.UTF8Encoding]::new($false))
    $records.Add([ordered]@{
        testId = [string]$test.id
        evidencePath = Get-PortableRelativePath $recordPath
        evidenceSha256 = Get-Sha256 $recordPath
        verdict = [string]$record.verdict
        executedTestCount = [int]$testCount
    })
    if (-not $passed) {
        break
    }
}

$executedTestTotal = 0
foreach ($resultRecord in $records) {
    $executedTestTotal += [int]$resultRecord['executedTestCount']
}
$manifest = [ordered]@{
    schemaVersion = 1
    scopeId = [string]$planValue.scopeId
    planPath = Get-PortableRelativePath $planPath
    planSha256 = Get-Sha256 $planPath
    sourceCommitSha = ([string]$sourceSha).Trim()
    sourceDirty = [bool]$sourceDirty
    generatedAt = [DateTimeOffset]::Now.ToString('o')
    verdict = if ($overallPassed -and $records.Count -eq @($planValue.tests).Count) { 'pass' } else { 'fail' }
    executedTestCount = $executedTestTotal
    tests = @($records)
}
$manifestPath = Join-Path $evidencePath 'manifest.json'
[System.IO.File]::WriteAllText($manifestPath, (($manifest | ConvertTo-Json -Depth 10) + "`n"), [System.Text.UTF8Encoding]::new($false))

if ($manifest.verdict -ne 'pass') {
    throw "Verified test plan failed. Evidence: $(Get-PortableRelativePath $manifestPath)"
}
Write-Output "VERIFIED_TEST_PLAN_PASS $($planValue.scopeId) tests=$($manifest.executedTestCount) evidence=$(Get-PortableRelativePath $manifestPath)"
