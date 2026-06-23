# 安装 hu Harness 到新项目

param(
    [Parameter(Mandatory = $true)]
    [string]$Target
)

$Hu = $PSScriptRoot
$Cursor = Join-Path $Target ".cursor"

if (-not (Test-Path $Target)) { Write-Error "Target not found: $Target"; exit 1 }

function Ensure-Dir($p) { if (-not (Test-Path $p)) { New-Item -ItemType Directory -Force -Path $p | Out-Null } }

Ensure-Dir $Cursor
Ensure-Dir (Join-Path $Cursor "feedforward")
Ensure-Dir (Join-Path $Cursor "pipeline")
Ensure-Dir (Join-Path $Cursor "feedback")
Ensure-Dir (Join-Path $Cursor "session")
Ensure-Dir (Join-Path $Cursor "session\issues")
Ensure-Dir (Join-Path $Cursor "knowledge\learnings")
Ensure-Dir (Join-Path $Cursor "skills\rough-to-prototype")
Ensure-Dir (Join-Path $Cursor "templates\design")
Ensure-Dir (Join-Path $Cursor "rules")

# 核心映射（Harness 结构 + Cursor 兼容别名）
Copy-Item (Join-Path $Hu "harness.yaml") (Join-Path $Cursor "harness.yaml") -Force
Copy-Item (Join-Path $Hu "feedforward\*") (Join-Path $Cursor "feedforward") -Force
Copy-Item (Join-Path $Hu "pipeline\*") (Join-Path $Cursor "pipeline") -Force
Copy-Item (Join-Path $Hu "feedback\*") (Join-Path $Cursor "feedback") -Force
Copy-Item (Join-Path $Hu "rules\*") (Join-Path $Cursor "rules") -Force
Copy-Item (Join-Path $Hu "templates\design\*") (Join-Path $Cursor "templates\design") -Force

# Cursor Skill / 知识库别名（工具发现路径）
Copy-Item (Join-Path $Hu "pipeline\runbook.md") (Join-Path $Cursor "skills\rough-to-prototype\SKILL.md") -Force
Copy-Item (Join-Path $Hu "feedback\checklist.md") (Join-Path $Cursor "skills\rough-to-prototype\checklist.md") -Force
Copy-Item (Join-Path $Hu "feedforward\defaults.md") (Join-Path $Cursor "skills\rough-to-prototype\defaults.md") -Force
Copy-Item (Join-Path $Hu "feedforward\contract.md") (Join-Path $Cursor "AUTONOMOUS.md") -Force
Copy-Item (Join-Path $Hu "feedforward\methodology.md") (Join-Path $Cursor "METHODOLOGY.md") -Force
Copy-Item (Join-Path $Hu "feedforward\reference-case.md") (Join-Path $Cursor "knowledge\reference-case.md") -Force
Copy-Item (Join-Path $Hu "feedforward\lessons.md") (Join-Path $Cursor "knowledge\prototype-pipeline-lessons.md") -Force
Copy-Item (Join-Path $Hu "feedforward\external-refs.md") (Join-Path $Cursor "knowledge\external-references.md") -Force
Copy-Item (Join-Path $Hu "feedback\learning-loop.md") (Join-Path $Cursor "knowledge\learning-loop.md") -Force

Copy-Item (Join-Path $Hu "state\session.template.json") (Join-Path $Cursor "session\state.template.json") -Force
Copy-Item (Join-Path $Hu "state\session.template.json") (Join-Path $Cursor "session\state.json") -Force
"" | Set-Content (Join-Path $Cursor "session\issues\registry.jsonl") -Encoding utf8
"" | Set-Content (Join-Path $Cursor "knowledge\learnings\.gitkeep") -Encoding utf8

# 项目根入口
Copy-Item (Join-Path $Hu "AGENTS.md") (Join-Path $Target "AGENTS.md") -Force
$reqEn = Join-Path $Target "requirement.template.md"
Copy-Item (Join-Path $Hu "templates\requirement.template.md") $reqEn -Force
Copy-Item $reqEn (Join-Path $Target "$([char]0x9700)$([char]0x6C42).template.md") -Force

Write-Host "Installed hu harness -> $Cursor"
Write-Host "Created $Target\AGENTS.md"
Write-Host "Create 需求.md and open Cursor — agent runs autonomously."
