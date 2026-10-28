#!/usr/bin/env python3
"""PreToolUse hook for Bash and PowerShell: guard `git push`.

Blocks (exit code 2, reason on stderr):
  - force pushes, deletes, and --all / --mirror pushes
  - pushes to main or master
  - pushes of a pipeline feature branch (one with state/<branch>/status.md)
    unless that status file shows `review: approved...`

Branches without a state folder (for example tooling branches) are allowed.
Everything that is not a `git push` passes straight through.
"""
import json
import os
import re
import shlex
import subprocess
import sys

PROTECTED = {"main", "master"}
# git global options that take a separate value argument
GIT_OPTS_WITH_VALUE = {"-C", "-c", "--git-dir", "--work-tree", "--namespace"}


def block(msg):
    print(f"Blocked git push: {msg}", file=sys.stderr)
    sys.exit(2)


def tokens_of(segment):
    try:
        return shlex.split(segment)
    except ValueError:
        return segment.split()


def push_args(command):
    """Yield the argument list of every `git push` found in the command."""
    for segment in re.split(r"&&|\|\||;|\||\n", command):
        tokens = tokens_of(segment)
        for tok in tokens:
            # `bash -c "git push"` and similar: look inside quoted strings
            if re.search(r"\s", tok) and "git" in tok and "push" in tok:
                yield from push_args(tok)
        if "git" not in tokens:
            continue
        rest = tokens[tokens.index("git") + 1:]
        i = 0
        while i < len(rest) and rest[i].startswith("-"):
            i += 2 if rest[i] in GIT_OPTS_WITH_VALUE else 1
        if i < len(rest) and rest[i] == "push":
            yield rest[i + 1:]


def current_branch(project_dir):
    out = subprocess.run(
        ["git", "branch", "--show-current"],
        cwd=project_dir, capture_output=True, text=True,
    ).stdout.strip()
    if not out:
        block("cannot tell which branch is checked out (detached HEAD?)")
    return out


def target_branches(args, project_dir):
    positional = []
    for a in args:
        if a.startswith("--force") or a in ("--mirror", "--all", "--delete"):
            block(f"`{a}` is not allowed (no force pushes, deletes, or all-branch pushes)")
        if re.fullmatch(r"-[A-Za-z]*[fd][A-Za-z]*", a):
            block(f"`{a}` is not allowed (no force pushes or deletes)")
        if a.startswith("-"):
            continue
        positional.append(a)

    refspecs = positional[1:]  # positional[0] is the remote
    if not refspecs:
        return [current_branch(project_dir)]

    branches = []
    for spec in refspecs:
        if spec.startswith("+"):
            block(f"refspec `{spec}` forces the push")
        src, _, dst = spec.rpartition(":")
        if ":" in spec and not src:
            block(f"refspec `{spec}` deletes a remote branch")
        dst = re.sub(r"^refs/heads/", "", dst)
        branches.append(current_branch(project_dir) if dst == "HEAD" else dst)
    return branches


def check_branch(branch, project_dir):
    if branch in PROTECTED:
        block(f"pushing to `{branch}` is not allowed; use a feature branch and a pull request")
    if not re.fullmatch(r"[A-Za-z0-9._/-]+", branch) or ".." in branch:
        block(f"unusual branch name `{branch}`")
    status = os.path.join(project_dir, "state", branch, "status.md")
    if not os.path.isfile(status):
        return  # not a pipeline feature branch
    with open(status, encoding="utf-8") as f:
        if any(re.match(r"review:\s*approved", line) for line in f):
            return
    block(
        f"`{branch}` has no approved review in state/{branch}/status.md. "
        "Run the reviewer (review only, then push mode) so it approves the change first."
    )


def main():
    raw = sys.stdin.read()
    try:
        payload = json.loads(raw)
        command = payload.get("tool_input", {}).get("command", "") or ""
    except (ValueError, AttributeError):
        return
    if "push" not in command:
        return
    project_dir = os.environ.get("CLAUDE_PROJECT_DIR") or payload.get("cwd") or os.getcwd()
    try:
        for args in push_args(command):
            for branch in target_branches(args, project_dir):
                check_branch(branch, project_dir)
    except SystemExit:
        raise
    except Exception as exc:  # fail closed for anything that looked like a push
        if re.search(r"\bgit\b.*\bpush\b", command):
            block(f"could not check this push ({exc})")


if __name__ == "__main__":
    main()
