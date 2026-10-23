---
name: release-pr
description: Open an nf-snapshot release PR in one step. Bumps the version, finalizes the changelog, runs checks, and opens the PR. Merging it publishes the release. Use when asked to release or prepare a release.
---

# Release nf-snapshot

Invoking this skill authorizes creating a branch, committing, pushing, and
opening a PR. Never merge the PR or publish manually. Merging is the user's
review step. After CI passes on `main`, `.github/workflows/publish.yml`
publishes the version in `build.gradle`, tags `v{VERSION}`, and creates the
GitHub release from the matching changelog section.

1. Start from an up-to-date `main` with a clean working tree. Stop if the
   `v{VERSION}` tag already exists.
2. Version: use the requested one. If none is given, bump the minor version
   when the release adds features and the patch version otherwise.
3. `CHANGELOG.md`: rename `## [Unreleased]` to `## [{VERSION}]` and add an
   empty `## [Unreleased]` above it. If the section is empty, draft entries
   from merged PRs since the last tag (`git log v{PREV}..HEAD`) in the
   existing style.
4. Replace the previous version with the new one in `build.gradle` and in
   `nf-snapshot@{PREV}` references in `README.md` and `docs/src/content/docs/`.
5. Run `make test`, `make e2e`, `pre-commit run --all-files`, and from `docs/`
   `npm run check`, `npm test`, and `npm run build`. Stop and report on failure.
6. On branch `release/{VERSION}`, commit `chore: prepare {VERSION} release`,
   push, and open a PR to `main` whose body is the changelog section.
7. Reply with the PR link and the changelog entries for review.
