# 从 examine2 同步「原型阶段」规约到 hu/
# 用法: .\scripts\pack-portable.ps1
#       .\scripts\pack-portable.ps1 -OutDir D:\other\.cursor

param(
    [string]$OutDir = (Join-Path (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)) "hu")
)

$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Cursor = Join-Path $Root ".cursor"

$Files = @(
    "METHODOLOGY.md",
    "NEW-PROJECT.md",
    "PORTABLE.md",
    "rules\prototype-from-requirement.mdc",
    "knowledge\prototype-pipeline-lessons.md",
    "knowledge\learning-loop.md",
    "knowledge\external-references.md",
    "skills\open-design.md",
    "skills\rough-to-prototype\SKILL.md",
    "skills\rough-to-prototype\checklist.md",
    "skills\rough-to-prototype\defaults.md",
    "templates\design\00-rough-input.md",
    "templates\design\01-prd-skeleton.md",
    "templates\design\02-design-package-skeleton.md",
    "templates\design\03-prototype-brief-skeleton.md",
    "templates\design\04-config-spec-skeleton.md",
    "templates\learning-entry.md",
    "templates\AGENTS.new-project.md",
    "workflows\phase-0-requirement-expansion.md",
    "workflows\phase-1-internal-design-complete.md",
    "workflows\phase-1-design-freeze.md",
    "architecture\gates.md",
    "architecture\issue-protocol.md",
    "session\state.template.json"
)

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $OutDir "knowledge\learnings") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $OutDir "session\issues") | Out-Null

foreach ($rel in $Files) {
    $src = Join-Path $Cursor $rel
    $dst = Join-Path $OutDir $rel
    $dstDir = Split-Path $dst -Parent
    if (-not (Test-Path $dstDir)) { New-Item -ItemType Directory -Force -Path $dstDir | Out-Null }
    if (Test-Path $src) {
        Copy-Item $src $dst -Force
        Write-Host "OK $rel"
    } else {
        Write-Warning "MISSING $rel"
    }
}

# 空 registry + learnings gitkeep
"" | Set-Content (Join-Path $OutDir "session\issues\registry.jsonl") -Encoding utf8
"" | Set-Content (Join-Path $OutDir "knowledge\learnings\.gitkeep") -Encoding utf8
Copy-Item (Join-Path $Cursor "session\state.template.json") (Join-Path $OutDir "session\state.json") -Force

Write-Host "`nDone -> $OutDir"
Write-Host "Next: copy hu/ to new project via hu/install.ps1, add 需求.md"
