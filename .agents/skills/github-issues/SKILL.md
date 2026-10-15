---
name: github-issues
description: Work with nf-snapshot GitHub issues using gh. Use when asked to inspect, triage, create, or update repository issues.
---

# Work with GitHub issues

Use `gh` from this checkout so it resolves the repository from its remote. Read
an issue with `gh issue view <number> --comments`; list issues with
`gh issue list` and narrow by state or existing labels. Inspect comments and
labels before proposing or making changes. Keep reports focused on
reproduction steps, expected and actual behavior, affected Nextflow versions,
and relevant logs or validation cases.

Read operations are appropriate for review and triage. Create, comment, label,
assign, or close issues only when the user asks for that specific change. Treat
pull requests separately from issues. Use existing labels; inspect the
repository's labels instead of importing another repository's triage scheme.
