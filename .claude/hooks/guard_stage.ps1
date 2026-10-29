# PreToolUse hook for the Agent tool: gate pipeline stages on state/<feature>/ files.
#
# Blocks (exit code 2, reason on stderr) when a stage is started before its inputs exist:
#   - coder    needs plan.md and `planning: done`; fix mode also needs changes.md
#   - tester   needs changes.md and `coding: done`
#   - reviewer needs `coding: done` and `testing: done`; push mode needs
#              `review: approved, awaiting push approval`
#
# The feature is taken from the agent prompt: a `state/<feature>` path, or the name of an
# existing state folder. Other agents (planner, Explore, ...) pass straight through.
# Runs on Windows PowerShell 5.1 and PowerShell 7.

function Block([string]$msg) {
    [Console]::Error.WriteLine('Blocked pipeline stage: ' + $msg)
    exit 2
}

function Find-Feature([string]$prompt, [string]$stateDir) {
    $names = @()
    if (Test-Path -LiteralPath $stateDir -PathType Container) {
        $names = @(Get-ChildItem -LiteralPath $stateDir -Directory | ForEach-Object { $_.Name })
    }
    $found = New-Object 'System.Collections.Generic.HashSet[string]' ([System.StringComparer]::Ordinal)
    foreach ($m in [regex]::Matches($prompt, 'state[/\\]([A-Za-z0-9._-]+)')) {
        if ($names -ccontains $m.Groups[1].Value) { [void]$found.Add($m.Groups[1].Value) }
    }
    if ($found.Count -eq 0) {
        foreach ($n in $names) {
            if ($prompt -cmatch ('(?<![A-Za-z0-9._-])' + [regex]::Escape($n) + '(?![A-Za-z0-9._-])')) {
                [void]$found.Add($n)
            }
        }
    }
    if ($found.Count -eq 1) { return @($found)[0] }
    if ($found.Count -eq 0) {
        Block 'cannot tell which feature this is for. Name it in the prompt, e.g. `state/<feature>/` (the folder must already exist).'
    }
    Block ('prompt names several features ({0}); run one at a time' -f ((@($found) | Sort-Object) -join ', '))
}

function Get-StatusLines([string]$featureDir) {
    $path = Join-Path $featureDir 'status.md'
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        Block ('{0} is missing, so the stage state is unknown' -f $path)
    }
    # tolerate markdown bullets: "- planning: done" reads as "planning: done"
    return @(Get-Content -LiteralPath $path -Encoding UTF8 | ForEach-Object { $_.Trim() -creplace '^[-*]\s+', '' })
}

function Test-Status($lines, [string]$pattern) {
    foreach ($line in $lines) { if ($line -cmatch ('^' + $pattern)) { return $true } }
    return $false
}

function Assert-File([string]$featureDir, [string]$name, [string]$stage) {
    $p = Join-Path $featureDir $name
    if (-not (Test-Path -LiteralPath $p -PathType Leaf)) {
        Block ('{0} needs {1} first, and it does not exist' -f $stage, $p)
    }
}

function Assert-Status($lines, [string]$pattern, [string]$human, [string]$stage) {
    if (-not (Test-Status $lines $pattern)) {
        Block ('{0} needs `{1}` in status.md. Current status: {2}' -f $stage, $human, ($lines -join ' | '))
    }
}

# --- main ---
$raw = [Console]::In.ReadToEnd()
try {
    $payload = $raw | ConvertFrom-Json
    $agent = [string]$payload.tool_input.subagent_type
    $prompt = [string]$payload.tool_input.prompt
}
catch { exit 0 }
if (@('coder', 'tester', 'reviewer') -cnotcontains $agent) { exit 0 }

$projectDir = if ($env:CLAUDE_PROJECT_DIR) { $env:CLAUDE_PROJECT_DIR }
              elseif ($payload.cwd) { [string]$payload.cwd }
              else { (Get-Location).Path }
$stateDir = Join-Path $projectDir 'state'
$feature = Find-Feature $prompt $stateDir
$fdir = Join-Path $stateDir $feature
$lines = Get-StatusLines $fdir
$low = $prompt.ToLowerInvariant()

if ($agent -ceq 'coder') {
    Assert-File $fdir 'plan.md' 'coder'
    Assert-Status $lines 'planning:\s*done' 'planning: done' 'coder'
    if ($low.Contains('fix mode')) {
        Assert-File $fdir 'changes.md' 'coder (fix mode)'
        if (-not ((Test-Status $lines 'testing:\s*failed') -or (Test-Status $lines 'review:\s*changes requested'))) {
            Block ('fix mode needs `testing: failed` or `review: changes requested` in status.md. Current status: {0}' -f ($lines -join ' | '))
        }
    }
    else {
        Assert-Status $lines 'coding:\s*pending' 'coding: pending' 'coder (implement mode)'
    }
}
elseif ($agent -ceq 'tester') {
    Assert-File $fdir 'changes.md' 'tester'
    Assert-Status $lines 'coding:\s*done' 'coding: done' 'tester'
}
else {  # reviewer
    if ($low.Contains('push mode') -and -not $low.Contains('review only')) {
        Assert-Status $lines 'review:\s*approved, awaiting push approval' 'review: approved, awaiting push approval' 'reviewer (push mode)'
    }
    else {
        Assert-File $fdir 'tests.md' 'reviewer'
        Assert-Status $lines 'coding:\s*done' 'coding: done' 'reviewer'
        Assert-Status $lines 'testing:\s*done' 'testing: done' 'reviewer'
    }
}
exit 0
