---
title: Contributing
description: Build and test the plugin, work on documentation, and understand versioned publishing.
---

## Plugin development

Use Java 17 or later. We compile the plugin against Nextflow `26.04.6`.

```bash
make test
make e2e
pre-commit run --all-files
```

`make test` runs the unit tests. `make e2e` installs the plugin in your local Nextflow plugins directory and runs `validation/run-tests.sh`, checking run output and exit statuses. It needs Nextflow and the tools used by the validation pipeline. Add validation scenarios for changes to observed behaviour and keep pipeline outputs deterministic.

CI runs lint, unit tests on Java 17 and 21, and end-to-end tests on Nextflow 26.04 and the latest stable release. Configuration additions belong in `SnapshotConfig`, its tests, the README options table, and the documentation reference.

To install a development build for use by another pipeline:

```bash
make install
```

## Documentation development

The site uses [Astro Starlight](https://starlight.astro.build/) and [starlight-codeblocks](https://ewels.github.io/starlight-codeblocks/) for annotated examples and the setup walkthrough. Use Node.js 22.12 or later, then run from the repository root:

```bash
cd docs
npm ci
npm run dev
```

Before submitting a change, run from `docs/`:

```bash
npm run check
npm test
npm run build
npm run preview
```

`check` validates the Astro project, `test` checks versioned-site assembly, and `build` creates the production site. Use the preview server to inspect navigation, search, desktop and mobile layouts, both themes, and copied code examples. Keep copied examples complete and runnable; annotation directives should not appear in copied code.

## Versioned documentation

The published site provides:

| URL path                  | Content                                                        |
| ------------------------- | -------------------------------------------------------------- |
| `/nf-snapshot/dev/`       | Documentation from `main`.                                     |
| `/nf-snapshot/<version>/` | Documentation built from that published release's tag.         |
| `/nf-snapshot/latest/`    | The release designated as the latest stable release on GitHub. |

The site root redirects to `latest`, or to `dev` before the first stable release. The version menu opens the selected version's homepage.

The docs workflow builds pull requests for validation. It deploys development updates from `main`, refreshes release documentation after the `Publish` workflow succeeds, and supports manual reruns. The workflow retains release sites on `gh-pages`, so development updates preserve archived versions. GitHub Pages Actions publishes the assembled site.

For initial repository setup, select **Settings → Pages → Build and deployment → Source → GitHub Actions**. A successful workflow run can then publish the site.

## Plugin releases

Bump `version` in `build.gradle` and add a matching `CHANGELOG.md` section. After CI succeeds on `main`, the `Publish` workflow checks for a tag matching that version. If none exists, it publishes to the Nextflow registry, creates the tag, and creates the GitHub release. It requires the `NF_PLUGIN_PUBLISH_TOKEN` repository secret.

Documentation publishing is separate from plugin publishing. Build each release archive from its release tag. Include published releases in the version menu; exclude drafts and prereleases.
