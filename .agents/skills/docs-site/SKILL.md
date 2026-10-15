---
name: docs-site
description: Update nf-snapshot's Starlight documentation or diagnose its GitHub Pages publishing workflow. Use for docs changes, site builds, or Pages setup questions.
---

# Maintain the documentation site

Keep user guidance consistent with the plugin, README, validation pipeline,
and `.github/workflows/docs.yml`. The site is Astro Starlight with
`starlight-codeblocks`; keep examples complete and runnable, and ensure copied
code does not contain annotation directives.

## Content and checks

- Update the relevant page under `docs/src/content/docs/`; update `README.md`
  when the quick start, options, or general user contract changes.
- Use `docs/src/content/docs/reference.md` for configuration defaults and
  `docs/src/content/docs/troubleshooting.md` for recovery steps.
- Run `npm ci`, `npm run check`, `npm test`, and `npm run build` from `docs/`
  when validating a documentation change.
- Use `docs/scripts/publish.mjs` as the source of truth for versioned site
  assembly. Do not hand-edit generated site output.

## GitHub Pages

The workflow deploys on `main` and after a successful `Publish` workflow. For
initial setup, repository settings must use **Settings → Pages → Build and
deployment → Source → GitHub Actions**. A deployment failure caused by Pages
being disabled is fixed in repository settings; rerun the failed workflow after
enabling it. Verify the actual Actions run before claiming the site is live.

Do not change repository settings, rerun workflows, or publish unless the user
asks for that action.
