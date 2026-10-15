import starlight from '@astrojs/starlight';
import { defineConfig } from 'astro/config';
import codeblocks from 'starlight-codeblocks';

const version = process.env.DOCS_VERSION || 'dev';
if (!/^(dev|latest|\d+\.\d+\.\d+)$/.test(version)) {
  throw new Error(`Invalid documentation version: ${version}`);
}

export default defineConfig({
  site: 'https://adamrtalbot.github.io',
  base: `/nf-snapshot/${version}/`,
  trailingSlash: 'always',
  integrations: [
    starlight({
      title: 'nf-snapshot',
      description: 'Snapshot testing for the files your Nextflow pipeline publishes.',
      social: [
        { icon: 'github', label: 'GitHub', href: 'https://github.com/adamrtalbot/nf-snapshot' },
      ],
      editLink: {
        baseUrl: 'https://github.com/adamrtalbot/nf-snapshot/edit/main/docs/',
      },
      components: { ThemeSelect: './src/components/ThemeSelect.astro' },
      customCss: ['./src/styles/custom.css'],
      plugins: [codeblocks()],
      sidebar: [
        { label: 'Start here', items: [
          { label: 'Overview', slug: '' },
          { label: 'Getting started', slug: 'getting-started' },
        ] },
        { label: 'Guides', items: [
          { label: 'Usage', slug: 'usage' },
          { label: 'Examples', slug: 'examples' },
          { label: 'Troubleshooting', slug: 'troubleshooting' },
        ] },
        { label: 'Reference', slug: 'reference' },
        { label: 'Contributing', slug: 'contributing' },
      ],
    }),
  ],
});
