#!/usr/bin/env python3
"""PreToolUse hook for the Agent tool: gate pipeline stages on state/<feature>/ files.

Blocks (exit code 2, reason on stderr) when a stage is started before its inputs exist:
  - coder    needs plan.md and `planning: done`; fix mode also needs changes.md
  - tester   needs changes.md and `coding: done`
  - reviewer needs `coding: done` and `testing: done`; push mode needs
             `review: approved, awaiting push approval`

The feature is taken from the agent prompt: a `state/<feature>` path, or the name of an
existing state folder. Other agents (planner, Explore, ...) pass straight through.
"""
import json
import os
import re
import sys


def block(msg):
    print(f"Blocked pipeline stage: {msg}", file=sys.stderr)
    sys.exit(2)


def find_feature(prompt, state_dir):
    names = [d for d in os.listdir(state_dir)
             if os.path.isdir(os.path.join(state_dir, d))] if os.path.isdir(state_dir) else []
    found = {m for m in re.findall(r"state[/\\]([A-Za-z0-9._-]+)", prompt) if m in names}
    if not found:
        found = {n for n in names
                 if re.search(r"(?<![A-Za-z0-9._-])" + re.escape(n) + r"(?![A-Za-z0-9._-])", prompt)}
    if len(found) == 1:
        return found.pop()
    if not found:
        block("cannot tell which feature this is for. Name it in the prompt, "
              "e.g. `state/<feature>/` (the folder must already exist).")
    block(f"prompt names several features ({', '.join(sorted(found))}); run one at a time")


def status_lines(feature_dir):
    path = os.path.join(feature_dir, "status.md")
    if not os.path.isfile(path):
        block(f"{path} is missing, so the stage state is unknown")
    with open(path, encoding="utf-8") as f:
        return [line.strip() for line in f]


def has_status(lines, pattern):
    return any(re.match(pattern, line) for line in lines)


def need_file(feature_dir, name, stage):
    if not os.path.isfile(os.path.join(feature_dir, name)):
        block(f"{stage} needs {os.path.join(feature_dir, name)} first, and it does not exist")


def need_status(lines, pattern, human, stage):
    if not has_status(lines, pattern):
        block(f"{stage} needs `{human}` in status.md. Current status: {' | '.join(lines)}")


def main():
    try:
        payload = json.loads(sys.stdin.read())
        tool_input = payload.get("tool_input", {})
        agent = tool_input.get("subagent_type") or ""
        prompt = tool_input.get("prompt") or ""
    except (ValueError, AttributeError):
        return
    if agent not in ("coder", "tester", "reviewer"):
        return

    project_dir = os.environ.get("CLAUDE_PROJECT_DIR") or payload.get("cwd") or os.getcwd()
    state_dir = os.path.join(project_dir, "state")
    feature = find_feature(prompt, state_dir)
    fdir = os.path.join(state_dir, feature)
    lines = status_lines(fdir)
    low = prompt.lower()

    if agent == "coder":
        need_file(fdir, "plan.md", "coder")
        need_status(lines, r"planning:\s*done", "planning: done", "coder")
        if "fix mode" in low:
            need_file(fdir, "changes.md", "coder (fix mode)")
            if not (has_status(lines, r"testing:\s*failed") or has_status(lines, r"review:\s*changes requested")):
                block("fix mode needs `testing: failed` or `review: changes requested` in status.md. "
                      f"Current status: {' | '.join(lines)}")
        else:
            need_status(lines, r"coding:\s*pending", "coding: pending", "coder (implement mode)")
    elif agent == "tester":
        need_file(fdir, "changes.md", "tester")
        need_status(lines, r"coding:\s*done", "coding: done", "tester")
    else:  # reviewer
        if "push mode" in low and "review only" not in low:
            need_status(lines, r"review:\s*approved, awaiting push approval",
                        "review: approved, awaiting push approval", "reviewer (push mode)")
        else:
            need_file(fdir, "tests.md", "reviewer")
            need_status(lines, r"coding:\s*done", "coding: done", "reviewer")
            need_status(lines, r"testing:\s*done", "testing: done", "reviewer")


if __name__ == "__main__":
    main()
