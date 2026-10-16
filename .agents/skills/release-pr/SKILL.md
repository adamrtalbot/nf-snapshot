---
name: release-pr
description: Prepare an nf-snapshot plugin release change and pull request using the repository's automated publish workflow. Use when asked to prepare a release.
---

# Prepare a plugin release

Before editing, read the current version in `build.gradle`, the relevant
`CHANGELOG.md` entries, and `.github/workflows/publish.yml`. The workflow
publishes the version from `build.gradle` only after CI succeeds on `main` (or
when manually dispatched), and only when `NF_PLUGIN_PUBLISH_TOKEN` is set and
the `v{VERSION}` tag does not already exist. It then creates that tag and a
GitHub release using the matching changelog section.

When the target version or release notes are missing, ask for them before
changing files. Follow the existing changelog format. Update version
references only where they describe the current release, and keep the patch
limited to release preparation. Run relevant unit, end-to-end, and docs checks
when changes affect them.

Create a branch, commit, push, or PR only when the user explicitly requests
those actions. Never merge the release PR or manually publish the plugin as
part of preparation.
