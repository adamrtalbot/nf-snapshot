# Agent guide

`nf-snapshot` is a Nextflow plugin that compares the files a workflow publishes
with a committed JSON snapshot. Read `CLAUDE.md` for architecture, validation,
and release details. `README.md` and `docs/src/content/docs/` describe the user
contract; keep them aligned with the implementation.

## Workflows

Reusable agent skills live in `.agents/skills/`; any agent should follow the
matching `SKILL.md` for these tasks. Claude command entry points in
`.claude/commands/` route to the same workflows.

- `plugin-review`: review realistic plugin and documentation issues, prioritizing user experience.
- `docs-site`: maintain the Starlight site and diagnose its GitHub Pages workflow.
- `release-pr`: for any release request, open a release PR (version bump,
  changelog, checks); merging it publishes the release.
- `github-issues`: inspect or manage repository issues with `gh` when requested.

## Project guardrails

- Preserve snapshot format and comparison behavior unless the task asks to change them.
- Keep documented configuration options aligned across `SnapshotConfig`, tests,
  `README.md`, and the docs reference.
- Use `make test` for unit tests, `make e2e` for Nextflow integration scenarios,
  and `pre-commit run --all-files` for linting.
- Docs checks run from `docs/`: `npm run check`, `npm test`, and `npm run build`.
- Never publish, push, create a release, or modify GitHub issues unless the user
  explicitly requests that action. A release request authorizes the branch,
  commit, push, and PR steps in `release-pr`, but never merging or publishing.
