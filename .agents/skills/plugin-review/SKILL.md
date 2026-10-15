---
name: plugin-review
description: Review nf-snapshot for realistic user-facing bugs, documentation mismatches, and release blockers. Use when asked to review this plugin or its changes.
---

# Review nf-snapshot

Review the current branch or requested scope without changing files. Prioritize
issues users can reproduce, especially setup, snapshot creation and updates,
comparison results, recovery, and documentation accuracy. Do not inflate
intermittent or speculative risks.

## Read first

- `AGENTS.md` and `CLAUDE.md`
- `README.md` and the relevant pages under `docs/src/content/docs/`
- The implementation, tests, and validation scenarios touched by the claim

Trace relevant behavior end to end: config parsing, observer lifecycle, file
capture and hashing, snapshot serialization, comparison, and the final run
status. Check whether the docs give users a correct next step when something
fails. Compare published behavior against tests and runnable validation cases.

## Report

List only actionable findings, highest impact first. Each finding must include
severity, file and line, a concrete trigger, user impact, and the smallest fix.
Separate verified behavior from inference. If there are no findings, say so and
summarize exactly which checks ran; do not imply unrun compatibility or remote
deployment checks passed.
