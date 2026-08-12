param(
    [string]$Root = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
$rootPath = (Resolve-Path -LiteralPath $Root).Path
$validator = Join-Path $rootPath 'scripts/validate-project-progress.ps1'
$renderer = Join-Path $rootPath 'scripts/render-project-progress.ps1'
$fixtureRoot = Join-Path $rootPath 'scripts/tests/progress-validator'
$utf8 = New-Object System.Text.UTF8Encoding($false, $true)

function Read-Json([string]$Path) {
    return ($utf8.GetString([System.IO.File]::ReadAllBytes($Path)) | ConvertFrom-Json)
}

function Write-Utf8([string]$Path, [string]$Content) {
    [System.IO.File]::WriteAllText($Path, $Content, $utf8)
}

function Write-CaseFiles($Model, [string]$Markdown, [string]$CaseRoot) {
    Write-Utf8 (Join-Path $CaseRoot 'project-progress.json') ($Model | ConvertTo-Json -Depth 100)
    Write-Utf8 (Join-Path $CaseRoot 'PROJECT_PROGRESS.md') $Markdown
}

if (-not (Test-Path -LiteralPath $validator -PathType Leaf)) {
    throw "Validator does not exist: $validator"
}

$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) (
    'project-progress-validator-' + [Guid]::NewGuid().ToString('N'))
[void](New-Item -ItemType Directory -Path $tempRoot)
try {
    $fixtureEvidenceDirectories = @(
        'evidence-header', 'evidence-assignment', 'evidence-code', 'evidence-stray'
    )
    foreach ($directory in $fixtureEvidenceDirectories) {
        Copy-Item -LiteralPath (Join-Path $fixtureRoot $directory) -Destination $tempRoot -Recurse
    }

    $positiveMarkdown = $utf8.GetString(
        [System.IO.File]::ReadAllBytes((Join-Path $fixtureRoot 'positive.md')))
    foreach ($evidencePath in @(
            'evidence-header/acceptance.md',
            'evidence-assignment/acceptance.md',
            'evidence-code/acceptance.md')) {
        $model = Read-Json (Join-Path $fixtureRoot 'positive.json')
        $model.outcomes[0].evidencePath = $evidencePath
        $model.packageCheckpoints[0].verificationCommands[0].evidencePath = $evidencePath
        Write-CaseFiles $model $positiveMarkdown $tempRoot
        & $validator -Root $tempRoot -JsonPath 'project-progress.json' `
            -MarkdownPath 'PROJECT_PROGRESS.md' | Out-Null
    }
    & $renderer -Root $tempRoot -JsonPath 'project-progress.json' `
        -OutputPath 'GENERATED_PROJECT_PROGRESS.md' | Out-Null
    $generatedMarkdown = $utf8.GetString([System.IO.File]::ReadAllBytes(
        (Join-Path $tempRoot 'GENERATED_PROJECT_PROGRESS.md')))
    if ($generatedMarkdown -ne $positiveMarkdown) {
        throw 'Canonical progress renderer did not reproduce the positive Markdown fixture.'
    }

    $negativeFixtures = @(Get-ChildItem -LiteralPath $fixtureRoot -Filter 'negative-*.fixture.json' |
        Sort-Object Name)
    if ($negativeFixtures.Count -lt 4) {
        throw 'At least four negative progress-validator fixtures are required.'
    }
    foreach ($fixtureFile in $negativeFixtures) {
        $fixture = Read-Json $fixtureFile.FullName
        $model = Read-Json (Join-Path $fixtureRoot 'positive.json')
        $markdown = $positiveMarkdown
        switch ([string]$fixture.case) {
            'arithmetic' {
                $model.summary.percentage = 50.1
            }
            'duplicate-id' {
                $model.outcomes[2].id = $model.outcomes[1].id
            }
            'dependency-cycle' {
                $model.outcomes[1].status = 'remaining'
                $model.outcomes[1].dependsOn = @('OUTCOME-DEFERRED')
                $model.currentNode = $null
                $model.currentOutcomeId = $null
                $model.nextOutcomeId = 'OUTCOME-BUILD'
                $model.summary.counts.inProgress = 0
                $model.summary.counts.remaining = 1
                $model.summary.outcomeUnits.inProgress = 0
                $model.summary.outcomeUnits.remaining = 1
            }
            'fourth-checkpoint' {
                $fourth = Read-Json (Join-Path $fixtureRoot 'positive.json')
                $fourth.packageCheckpoints[2].id = 'CP4_FORBIDDEN'
                $model.packageCheckpoints = @($model.packageCheckpoints) +
                    @($fourth.packageCheckpoints[2])
            }
            'invalid-dependency' {
                $model.outcomes[1].dependsOn = @('OUTCOME-DEFERRED')
            }
            'attempt-limit' {
                $model.packageCheckpoints[0].attempts = @(
                    [pscustomobject]@{ id = 'CP1-FAIL-1'; attemptedAt = '2026-08-04T00:01:00Z'; status = 'fail'; packagePath = $null; sha256 = $null; evidencePath = 'evidence-header/acceptance.md' },
                    [pscustomobject]@{ id = 'CP1-FAIL-2'; attemptedAt = '2026-08-04T00:02:00Z'; status = 'fail'; packagePath = $null; sha256 = $null; evidencePath = 'evidence-header/acceptance.md' },
                    [pscustomobject]@{ id = 'CP1-FAIL-3'; attemptedAt = '2026-08-04T00:03:00Z'; status = 'fail'; packagePath = $null; sha256 = $null; evidencePath = 'evidence-header/acceptance.md' },
                    [pscustomobject]@{ id = 'CP1-FAIL-4'; attemptedAt = '2026-08-04T00:04:00Z'; status = 'fail'; packagePath = $null; sha256 = $null; evidencePath = 'evidence-header/acceptance.md' }
                )
            }
            'markdown-drift' {
                $markdown = $markdown.Replace(
                    '# Project Progress', '# Stale Project Progress')
            }
            'missing-evidence' {
                $model.outcomes[0].evidencePath = 'missing/acceptance.md'
            }
            'stray-passed-evidence' {
                $model.outcomes[0].evidencePath = 'evidence-stray/acceptance.md'
                $model.packageCheckpoints[0].verificationCommands[0].evidencePath =
                    'evidence-stray/acceptance.md'
            }
            default {
                throw "Unknown negative fixture case: $($fixture.case)"
            }
        }
        Write-CaseFiles $model $markdown $tempRoot
        $failedAsExpected = $false
        try {
            & $validator -Root $tempRoot -JsonPath 'project-progress.json' `
                -MarkdownPath 'PROJECT_PROGRESS.md' | Out-Null
        } catch {
            if ($_.Exception.Message -notlike "*$($fixture.expectedMessage)*") {
                throw "Negative fixture $($fixtureFile.Name) failed for the wrong reason: $($_.Exception.Message)"
            }
            $failedAsExpected = $true
        }
        if (-not $failedAsExpected) {
            throw "Negative fixture $($fixtureFile.Name) unexpectedly passed."
        }
    }

    Write-Output "Project progress validator self-test passed: 3 PASS forms, $($negativeFixtures.Count) negative fixtures."
} finally {
    $resolvedTempRoot = [System.IO.Path]::GetFullPath($tempRoot)
    $systemTempPrefix = [System.IO.Path]::GetFullPath(
        [System.IO.Path]::GetTempPath()).TrimEnd(
            [System.IO.Path]::DirectorySeparatorChar) +
        [System.IO.Path]::DirectorySeparatorChar
    if ($resolvedTempRoot.StartsWith(
            $systemTempPrefix, [System.StringComparison]::OrdinalIgnoreCase) -and
        [System.IO.Path]::GetFileName($resolvedTempRoot).StartsWith(
            'project-progress-validator-', [System.StringComparison]::Ordinal)) {
        Remove-Item -LiteralPath $resolvedTempRoot -Recurse -Force
    }
}
