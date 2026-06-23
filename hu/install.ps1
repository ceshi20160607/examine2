# 安装 hu 规约包到新项目
# 用法: .\install.ps1 -Target "D:\path\to\your-java-project"

param(
    [Parameter(Mandatory = $true)]
    [string]$Target
)

$HuRoot = $PSScriptRoot
$CursorDir = Join-Path $Target ".cursor"

if (-not (Test-Path $Target)) {
    Write-Error "Target not found: $Target"
    exit 1
}

New-Item -ItemType Directory -Force -Path $CursorDir | Out-Null

$Exclude = @("install.ps1", "README.md", "TEST-GAP-ANALYSIS.md")
Get-ChildItem -Path $HuRoot -Force | Where-Object {
    $_.Name -notin $Exclude
} | ForEach-Object {
    Copy-Item -Path $_.FullName -Destination $CursorDir -Recurse -Force
}

# state.json from template
$Template = Join-Path $CursorDir "session\state.template.json"
$State = Join-Path $CursorDir "session\state.json"
if (Test-Path $Template) {
    Copy-Item $Template $State -Force
}

# optional AGENTS.md at project root
$AgentsSrc = Join-Path $CursorDir "templates\AGENTS.new-project.md"
$AgentsDst = Join-Path $Target "AGENTS.md"
if ((Test-Path $AgentsSrc) -and -not (Test-Path $AgentsDst)) {
    Copy-Item $AgentsSrc $AgentsDst -Force
    Write-Host "Created AGENTS.md"
}

Write-Host ""
Write-Host "Installed hu -> $CursorDir"
Write-Host "Next:"
Write-Host "  1. Create 需求.md at project root"
Write-Host "  2. Tell Agent: read .cursor/METHODOLOGY.md and run rough-to-prototype from L0"
Write-Host "  3. See .cursor/NEW-PROJECT.md"
