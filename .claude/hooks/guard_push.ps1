# PreToolUse hook for Bash and PowerShell: guard `git push`.
#
# Blocks (exit code 2, reason on stderr):
#   - force pushes, deletes, and --all / --mirror pushes
#   - pushes to main or master
#   - pushes of a pipeline feature branch (one with state/<branch>/status.md)
#     unless that status file shows `review: approved...`
#
# Branches without a state folder (for example tooling branches) are allowed.
# Everything that is not a `git push` passes straight through.
# Runs on Windows PowerShell 5.1 and PowerShell 7.

$Protected = @('main', 'master')
# git global options that take a separate value argument
$GitOptsWithValue = @('-C', '-c', '--git-dir', '--work-tree', '--namespace')
$Redirect = [regex]'^(\d*|&)(>>?|<)(&\d+|-)?(.*)$'

function Block([string]$msg) {
    [Console]::Error.WriteLine('Blocked git push: ' + $msg)
    exit 2
}

# POSIX-style word splitting (quotes and backslash escapes), like Python's shlex.split.
function Split-Shell([string]$s) {
    $tokens = New-Object 'System.Collections.Generic.List[string]'
    $sb = New-Object System.Text.StringBuilder
    $inTok = $false
    $q = ''
    $n = $s.Length
    for ($i = 0; $i -lt $n; $i++) {
        $c = [string]$s[$i]
        if ($q -ceq "'") {
            if ($c -ceq "'") { $q = '' } else { [void]$sb.Append($c) }
        }
        elseif ($q -ceq '"') {
            if ($c -ceq '"') { $q = '' }
            elseif ($c -ceq '\' -and ($i + 1) -lt $n -and ([string]$s[$i + 1] -ceq '"' -or [string]$s[$i + 1] -ceq '\')) {
                $i++
                [void]$sb.Append([string]$s[$i])
            }
            else { [void]$sb.Append($c) }
        }
        elseif ($c -match '\s') {
            if ($inTok) { $tokens.Add($sb.ToString()); [void]$sb.Clear(); $inTok = $false }
        }
        elseif ($c -ceq "'" -or $c -ceq '"') { $q = $c; $inTok = $true }
        elseif ($c -ceq '\') {
            if (($i + 1) -ge $n) { throw 'No escaped character' }
            $i++
            [void]$sb.Append([string]$s[$i])
            $inTok = $true
        }
        else { [void]$sb.Append($c); $inTok = $true }
    }
    if ($q -ne '') { throw 'No closing quotation' }
    if ($inTok) { $tokens.Add($sb.ToString()) }
    return $tokens.ToArray()
}

function Get-Tokens([string]$segment) {
    try { return @(Split-Shell $segment) }
    catch { return @($segment -split '\s+' | Where-Object { $_ -ne '' }) }
}

# Drop shell redirections (`2>&1`, `>out.txt`, `2> err`, `&>log`) from a token list.
function Remove-Redirects([string[]]$tokens) {
    $out = New-Object 'System.Collections.Generic.List[string]'
    $skipNext = $false
    foreach ($tok in $tokens) {
        if ($skipNext) { $skipNext = $false; continue }
        $m = $Redirect.Match($tok)
        if ($m.Success) {
            # a bare operator like `>` or `2>` takes the next token as its target
            $skipNext = ($m.Groups[3].Value -eq '') -and ($m.Groups[4].Value -eq '')
            continue
        }
        $out.Add($tok)
    }
    return $out.ToArray()
}

# Collect the argument list of every `git push` found in the command.
function Find-Push([string]$command, $found) {
    foreach ($segment in [regex]::Split($command, '&&|\|\||;|\||\n')) {
        $tokens = @(Get-Tokens $segment)
        foreach ($tok in $tokens) {
            # `bash -c "git push"` and similar: look inside quoted strings
            if ($tok -match '\s' -and $tok.Contains('git') -and $tok.Contains('push')) {
                Find-Push $tok $found
            }
        }
        $gitAt = [array]::IndexOf([string[]]$tokens, 'git')
        if ($gitAt -lt 0) { continue }
        $rest = @(if (($gitAt + 1) -lt $tokens.Count) { @($tokens[($gitAt + 1)..($tokens.Count - 1)]) } else { @() })
        $i = 0
        while ($i -lt $rest.Count -and $rest[$i].StartsWith('-')) {
            if ($GitOptsWithValue -ccontains $rest[$i]) { $i += 2 } else { $i += 1 }
        }
        if ($i -lt $rest.Count -and $rest[$i] -ceq 'push') {
            $pushArgs = @(if (($i + 1) -lt $rest.Count) { @($rest[($i + 1)..($rest.Count - 1)]) } else { @() })
            [void]$found.Add(@(Remove-Redirects $pushArgs))
        }
    }
}

function Get-CurrentBranch([string]$projectDir) {
    Push-Location -LiteralPath $projectDir
    try { $out = ((& git branch --show-current) | Out-String).Trim() }
    finally { Pop-Location }
    if (-not $out) { Block 'cannot tell which branch is checked out (detached HEAD?)' }
    return $out
}

function Get-TargetBranches($pushArgs, [string]$projectDir) {
    $positional = @()
    foreach ($a in $pushArgs) {
        if ($a.StartsWith('--force') -or @('--mirror', '--all', '--delete') -ccontains $a) {
            Block ('`{0}` is not allowed (no force pushes, deletes, or all-branch pushes)' -f $a)
        }
        if ($a -cmatch '\A-[A-Za-z]*[fd][A-Za-z]*\z') {
            Block ('`{0}` is not allowed (no force pushes or deletes)' -f $a)
        }
        if ($a.StartsWith('-')) { continue }
        $positional += $a
    }

    # positional[0] is the remote
    $refspecs = @(if ($positional.Count -gt 1) { @($positional[1..($positional.Count - 1)]) } else { @() })
    if ($refspecs.Count -eq 0) { return @(Get-CurrentBranch $projectDir) }

    $branches = @()
    foreach ($spec in $refspecs) {
        if ($spec.StartsWith('+')) { Block ('refspec `{0}` forces the push' -f $spec) }
        $colon = $spec.LastIndexOf(':')
        $src = if ($colon -ge 0) { $spec.Substring(0, $colon) } else { '' }
        $dst = if ($colon -ge 0) { $spec.Substring($colon + 1) } else { $spec }
        if ($colon -ge 0 -and $src -eq '') { Block ('refspec `{0}` deletes a remote branch' -f $spec) }
        $dst = $dst -creplace '^refs/heads/', ''
        if ($dst -ceq 'HEAD') { $branches += Get-CurrentBranch $projectDir } else { $branches += $dst }
    }
    return $branches
}

function Test-Branch([string]$branch, [string]$projectDir) {
    if ($Protected -ccontains $branch) {
        Block ('pushing to `{0}` is not allowed; use a feature branch and a pull request' -f $branch)
    }
    if (($branch -cnotmatch '\A[A-Za-z0-9._/-]+\z') -or $branch.Contains('..')) {
        Block ('unusual branch name `{0}`' -f $branch)
    }
    $status = Join-Path (Join-Path (Join-Path $projectDir 'state') $branch) 'status.md'
    if (-not (Test-Path -LiteralPath $status -PathType Leaf)) { return }  # not a pipeline feature branch
    foreach ($line in @(Get-Content -LiteralPath $status -Encoding UTF8)) {
        if ($line.Trim() -cmatch '^(?:[-*]\s+)?review:\s*approved') { return }
    }
    Block ('`{0}` has no approved review in state/{0}/status.md. Run the reviewer (review only, then push mode) so it approves the change first.' -f $branch)
}

# --- main ---
$raw = [Console]::In.ReadToEnd()
try {
    $payload = $raw | ConvertFrom-Json
    $command = [string]$payload.tool_input.command
}
catch { exit 0 }
if (-not $command -or -not $command.Contains('push')) { exit 0 }

$projectDir = if ($env:CLAUDE_PROJECT_DIR) { $env:CLAUDE_PROJECT_DIR }
              elseif ($payload.cwd) { [string]$payload.cwd }
              else { (Get-Location).Path }

try {
    $found = New-Object System.Collections.ArrayList
    Find-Push $command $found
    foreach ($pushArgs in $found) {
        foreach ($branch in @(Get-TargetBranches $pushArgs $projectDir)) {
            Test-Branch $branch $projectDir
        }
    }
}
catch {
    # fail closed for anything that looked like a push
    if ($command -cmatch '\bgit\b.*\bpush\b') {
        Block ('could not check this push ({0})' -f $_.Exception.Message)
    }
}
exit 0
