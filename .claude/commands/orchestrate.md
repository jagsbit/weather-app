---
description: Build a feature end to end with the planner, coder, tester and reviewer subagents, driven by state/<feature>/status.md. Use "resume <feature>" to continue an interrupted run.
argument-hint: <feature description> | resume <feature>
disable-model-invocation: true
---

# Orchestrate a feature

Request: $ARGUMENTS

You are the orchestrator, a state machine over `state/<feature>/status.md`. Subagents cannot call each other, so you run them one at a time with the Agent tool. Do not write or edit code, tests or artifacts yourself.

**Never pick the next step from memory.** Each cycle: read `status.md`, find the matching row in the table below, do exactly that action, then read `status.md` again. Repeat until a row says DONE or STOP. Because all state is on disk, a run can be resumed at any point.

## Start

- **New feature** (the request is a description): run Pre-check, then action PLAN. This creates `state/<feature>/`.
- **Resume** (the request starts with `resume <feature>`): skip PLAN and go straight to the cycle with that feature. Check `git branch --show-current` is `main` or `<feature>`; otherwise tell the user and stop.

**Pre-check** (new features only): run `git status` and `git branch --show-current`. You must be on `main` with no uncommitted changes to tracked files (untracked files are fine). If not, tell the user and stop.

## State table

`status.md` has the lines `planning`, `branch`, `coding`, `testing`, `review`. Match the first row that fits.

| # | Status lines | Action |
|---|---|---|
| 1 | no `state/<feature>/` folder | PLAN |
| 2 | `coding: blocked ...` | STOP: show the reason |
| 3 | `planning: done`, `coding: pending` | APPROVE-PLAN, then CODE |
| 4 | `coding: done`, `testing: pending` | TEST |
| 5 | `testing: failed` | FIX with `tests.md` (if it lists no suspected bugs: STOP, show the failure) |
| 6 | `testing: done`, `review: pending` | REVIEW |
| 7 | `review: changes requested ...` | FIX with `review.md` (if an issue says the plan is wrong: STOP, show it) |
| 8 | `review: approved, awaiting push approval` | PUSH-CHECKPOINT, then PUSH |
| 9 | `review: approved, pushed ...` | DONE |
| 10 | `review: approved, push failed ...` | STOP: show the error; the commit is local |
| 11 | any `... in progress` line, or any combination not listed | STOP: an agent was interrupted or the state is unexpected. Show `status.md` and ask the user whether to re-run that stage |

## Actions

**PLAN.** Run the `planner` agent with the feature description. Take `<feature>` from the `state/<feature>/plan.md` path in its reply. The cycle then reaches row 3.

**APPROVE-PLAN.** Read `plan.md` and show the user a short summary (approach, files, open questions). Ask with AskUserQuestion whether to proceed, change the plan, or stop. Get open questions answered first. If the user wants changes, re-run the `planner` with their feedback and come back here. Do not continue without approval.

**CODE.** Run the `coder` agent in implement mode for `<feature>`.

**TEST.** Run the `tester` agent for `<feature>`.

**REVIEW.** Run the `reviewer` agent for `<feature>` in **review only** mode, so it does not commit or push. If it comes back `changes requested`, row 7 applies next.

**FIX.** First count the fix rounds already done: the number of `## Fix round` headings in `state/<feature>/changes.md`. If that is 3 or more, STOP: show the remaining failures or issues and ask the user whether to continue. Otherwise run the `coder` agent in **fix mode**. Tell it: "fix mode", the feature name, and the feedback file (`state/<feature>/tests.md` or `state/<feature>/review.md`). The coder resets `testing` and `review` to `pending` when it finishes, so the cycle runs TEST and then REVIEW again.

**PUSH-CHECKPOINT.** This is the human checkpoint before anything leaves the machine. Show the user the verdict from `review.md`, any plan deviations the reviewer accepted, the test result, and the output of `git diff --stat main...<feature>` plus `git status --short`. Ask with AskUserQuestion whether to push `<feature>` to GitHub or stop without pushing. Do not push without an explicit yes. If the answer is stop, tell the user nothing was committed or pushed and that the reviewer can be run in push mode later, then STOP.

**PUSH.** Run the `reviewer` agent in **push mode** for `<feature>`. The next row is 9 (pushed), 10 (push failed) or 6 (files changed since the review, so review again).

**DONE.** Report: the feature name and branch, the commit hash, the number of fix rounds used, the test result, and the pull request link (`https://github.com/<owner>/<repo>/pull/new/<feature>`, built from `git remote get-url origin`). Mention that `state/<feature>/` is local only.

**STOP.** Tell the user what state you stopped in and why, and what they can do next (for example `/orchestrate resume <feature>`).

## Rules

- Run agents sequentially, never in parallel, since each depends on the previous one's files.
- After every agent finishes, read `status.md` again. Never trust the agent's reply alone. If the reply and `status.md` disagree, STOP and show both.
- When briefing an agent, give it the feature name and the state folder path. Do not paste your own opinion of what the answer should be.
- Keep the user informed with one short line before each agent runs (for example "Running tester, fix round 1") and one line with the outcome after.
- Never push or commit yourself; only the reviewer does that. A hook in `.claude/settings.json` also blocks force pushes, pushes to `main`, and pushes of an unapproved feature branch. If it blocks something, stop and show the user instead of working around it.
- Never switch branches yourself.
