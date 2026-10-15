---
name: release-pr
description: Prepare an nf-snapshot plugin release change and pull request using the repository's automated publish workflow. Use when asked to prepare a release.
---

# Prepare a plugin release

Read the current version in `build.gradle`, the unreleased changes in
`CHANGELOG.md`, and `.github/workflows/publish.yml` before editing. The publish
workflow runs after CI succeeds on `main`, publishes the version from
`build.gradle`, then creates its tag and GitHub release. It requires
`NF_PLUGIN_PUBLISH_TOKEN`.

When the target version or release notes are missing, ask for those details
before changing files. Follow existing changelog format, update version
references only where they describe the current release, and keep the patch
limited to release preparation. Run relevant unit, end-to-end, and docs checks
when changes affect them.

Create a branch, commit, push, or PR only when explicitly requested. Never merge
the release PR or manually publish the plugin as part of preparation.
