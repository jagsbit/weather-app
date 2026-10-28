---
description: Build a feature end to end with the planner, coder, tester and reviewer subagents, looping back to the coder when tests fail or the review requests changes.
argument-hint: <feature description>
disable-model-invocation: true
---

# Orchestrate a feature

Build this feature: $ARGUMENTS

You are the orchestrator. Subagents cannot call each other, so you run them one at a time with the Agent tool and decide each next step from the state files. Do not write or edit code, tests or artifacts yourself.

All artifacts live in `state/<feature>/` (`plan.md`, `changes.md`, `tests.md`, `review.md`, `status.md`). **`status.md` is the source of truth**: after every agent finishes, read it instead of trusting the agent's reply alone.

## Flow

```
planner -> (user approves plan) -> coder -> tester -+-> failed -> coder (fix mode) -> tester ...
                                                    +-> passed -> reviewer -+-> approved: done
                                                                            +-> changes requested -> coder (fix mode) -> tester -> reviewer ...
```

## Steps

1. **Pre-check.** Run `git status` and `git branch --show-current`. You must be on `main` with no uncommitted changes to tracked files (untracked files are fine). If not, tell the user and stop.

2. **Plan.** Run the `planner` agent with the feature description above. Its reply gives the path `state/<feature>/plan.md`; take `<feature>` from it. Read the plan and show the user a short summary (approach, files, open questions). Ask the user with AskUserQuestion whether to proceed, change the plan, or stop. If the plan has open questions, get them answered first. If the user wants changes, re-run the planner with their feedback. Do not continue without approval.

3. **Code.** Run the `coder` agent in implement mode for `<feature>`. Read `status.md`: continue only if it shows `coding: done`. If it shows `coding: blocked`, show the reason to the user and stop.

4. **Test.** Run the `tester` agent for `<feature>`. Read `status.md` and `tests.md`.
   - `testing: done`: go to step 5.
   - `testing: failed`: if `tests.md` lists suspected bugs, go to the fix loop below with `tests.md` as the feedback. If it fails with no suspected bugs, show the failure to the user and stop.

5. **Review.** Run the `reviewer` agent for `<feature>`. Read `status.md` and `review.md`.
   - `review: approved, pushed to <feature> (...)`: done, go to step 6.
   - `review: approved, push failed (...)`: show the error to the user and stop. The commit is local.
   - `review: changes requested`: read the issues in `review.md`. If any says the plan itself is wrong, ambiguous or incomplete (not the code), show it to the user and ask how to proceed; do not send it to the coder. Otherwise go to the fix loop with `review.md` as the feedback.

6. **Finish.** Report to the user: the feature name and branch, the commit hash, the number of fix rounds used, the test result, and the link to open a pull request (`https://github.com/<owner>/<repo>/pull/new/<feature>`, built from `git remote get-url origin`). Mention that the `state/<feature>/` artifacts are local only.

## Fix loop

Keep a counter of fix rounds, starting at 0. Each time the coder is sent back, the counter goes up by one.

1. If the counter is already 3, stop and show the user the remaining failures or issues. Ask whether to continue.
2. Run the `coder` agent in **fix mode** for `<feature>`. Tell it explicitly: "fix mode", the feature name, and the feedback file (`state/<feature>/tests.md` or `state/<feature>/review.md`).
3. Read `status.md`. If it shows `coding: blocked`, show the reason to the user and stop. Otherwise run the `tester` agent again (step 4), then the `reviewer` (step 5) once tests pass. After a review-triggered fix you must still re-run the tester before the reviewer.

## Rules

- Run agents sequentially, never in parallel, since each depends on the previous one's files.
- When briefing an agent, give it the feature name and the state folder path. Do not paste your own opinion of what the answer should be.
- Keep the user informed with one short line before each agent runs (for example "Running tester, fix round 1") and one line with the outcome after.
- Never push or commit yourself; only the reviewer does that. Never switch branches yourself.
- If an agent's reply and `status.md` disagree, or `status.md` shows an unexpected state, stop and show the user instead of guessing.
