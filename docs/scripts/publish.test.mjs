import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import {
  chmod,
  mkdir,
  mkdtemp,
  readFile,
  rm,
  symlink,
  writeFile,
} from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import test from "node:test";
import { assembleSite, releaseVersion } from "./publish.mjs";

async function fixture(t) {
  const directory = await mkdtemp(
    path.join(tmpdir(), "nf-snapshot-publish-test-"),
  );
  t.after(() => rm(directory, { recursive: true, force: true }));
  const archive = path.join(directory, "archive");
  const output = path.join(directory, "output");
  const builds = [];
  const build = async ({ version, tag, destination }) => {
    builds.push({ version, tag });
    await mkdir(destination, { recursive: true });
    await writeFile(
      path.join(destination, "index.html"),
      `${version} from ${tag ?? "main"}`,
    );
  };
  return { archive, output, builds, build };
}

const release = (version, extra = {}) => ({
  tag_name: `v${version}`,
  draft: false,
  prerelease: false,
  ...extra,
});

test("before a release, root and latest redirect to development docs", async (t) => {
  const options = await fixture(t);
  const manifest = await assembleSite({
    ...options,
    releases: [],
    latestTag: null,
  });
  assert.deepEqual(manifest, { latest: null, versions: [] });
  assert.deepEqual(options.builds, [{ version: "dev", tag: null }]);
  assert.match(
    await readFile(path.join(options.output, "index.html"), "utf8"),
    /url=\.\/dev\//,
  );
  assert.match(
    await readFile(path.join(options.output, "latest/index.html"), "utf8"),
    /url=\.\.\/dev\//,
  );
});

test("builds exact release tags and follows designated latest rather than highest version", async (t) => {
  const options = await fixture(t);
  const manifest = await assembleSite({
    ...options,
    releases: [
      release("1.9.0"),
      release("1.10.0"),
      release("2.0.0-rc.1", { prerelease: true }),
      release("9.0.0", { draft: true }),
    ],
    latestTag: "v1.9.0",
  });
  assert.deepEqual(manifest, {
    latest: "1.9.0",
    versions: ["1.10.0", "1.9.0"],
  });
  assert.deepEqual(options.builds, [
    { version: "dev", tag: null },
    { version: "1.9.0", tag: "v1.9.0" },
    { version: "1.10.0", tag: "v1.10.0" },
    { version: "latest", tag: "v1.9.0" },
  ]);
  assert.match(
    await readFile(path.join(options.output, "latest/index.html"), "utf8"),
    /latest from v1\.9\.0/,
  );
  assert.match(
    await readFile(path.join(options.output, "index.html"), "utf8"),
    /url=\.\/latest\//,
  );
});

test("development updates retain immutable release sites and rebuild the latest alias", async (t) => {
  const options = await fixture(t);
  await mkdir(path.join(options.archive, "0.1.0"), { recursive: true });
  await writeFile(
    path.join(options.archive, "0.1.0/index.html"),
    "original release",
  );
  const manifest = await assembleSite({
    ...options,
    releases: [release("0.1.0"), release("0.2.0")],
    latestTag: "v0.2.0",
  });
  assert.deepEqual(manifest, { latest: "0.2.0", versions: ["0.2.0", "0.1.0"] });
  assert.equal(
    await readFile(path.join(options.output, "0.1.0/index.html"), "utf8"),
    "original release",
  );
  assert.deepEqual(
    options.builds.map((build) => build.version),
    ["dev", "0.2.0", "latest"],
  );
});

test("retains archives even if a release is no longer in the API response", async (t) => {
  const options = await fixture(t);
  await mkdir(path.join(options.archive, "0.1.0"), { recursive: true });
  await writeFile(
    path.join(options.archive, "0.1.0/index.html"),
    "original release",
  );
  const manifest = await assembleSite({
    ...options,
    releases: [],
    latestTag: null,
  });
  assert.deepEqual(manifest, { latest: null, versions: ["0.1.0"] });
});

test("rejects unsafe or non-stable release names before building", async (t) => {
  const options = await fixture(t);
  for (const tag of [
    "../escape",
    "/tmp/escape",
    "v1.2.3/../x",
    "--all",
    "v01.2.3",
    "v1.2.3-rc.1",
    "v1.2",
  ]) {
    assert.throws(() => releaseVersion(tag), /Invalid stable release/);
    await assert.rejects(
      assembleSite({
        ...options,
        releases: [{ tag_name: tag }],
        latestTag: null,
      }),
      /Invalid stable release/,
    );
  }
  assert.equal(releaseVersion("1.2.3"), "1.2.3");
  assert.equal(releaseVersion("v1.2.3"), "1.2.3");
  assert.deepEqual(options.builds, []);
});

test("failed builds never advance the published manifest or alter the archive", async (t) => {
  const options = await fixture(t);
  await mkdir(options.output, { recursive: true });
  await mkdir(options.archive, { recursive: true });
  const previous = '{"latest":null,"versions":[]}\n';
  await writeFile(path.join(options.output, "versions.json"), previous);
  await writeFile(path.join(options.archive, "versions.json"), previous);
  await assert.rejects(
    assembleSite({
      ...options,
      releases: [release("0.1.0")],
      latestTag: "v0.1.0",
      build: async (args) => {
        if (args.version === "latest") throw new Error("Build failed");
        await options.build(args);
      },
    }),
    /Build failed/,
  );
  assert.equal(
    await readFile(path.join(options.output, "versions.json"), "utf8"),
    previous,
  );
  assert.equal(
    await readFile(path.join(options.archive, "versions.json"), "utf8"),
    previous,
  );
});

test("rejects incomplete or linked site output", async (t) => {
  const options = await fixture(t);
  await assert.rejects(
    assembleSite({
      ...options,
      releases: [],
      latestTag: null,
      build: async ({ destination }) => {
        await mkdir(destination, { recursive: true });
      },
    }),
    /no index.html/,
  );
  await assert.rejects(
    assembleSite({
      ...options,
      releases: [],
      latestTag: null,
      build: async (args) => {
        await options.build(args);
        await symlink(options.archive, path.join(args.destination, "linked"));
      },
    }),
    /symbolic link/,
  );
});

test("CLI initializes and updates a local archive branch without overwriting releases", async (t) => {
  const options = await fixture(t);
  const root = path.dirname(options.archive);
  const repository = path.join(root, "repository");
  const remote = path.join(root, "remote.git");
  const bin = path.join(root, "bin");
  await mkdir(path.join(repository, "docs"), { recursive: true });
  await mkdir(bin);
  const git = (args, cwd = repository) =>
    execFileSync("git", args, {
      cwd,
      encoding: "utf8",
      stdio: ["ignore", "pipe", "pipe"],
    }).trim();
  git(["init", "--bare", remote], root);
  git(["init", "-b", "main"]);
  git(["config", "user.name", "Documentation test"]);
  git(["config", "user.email", "docs@example.test"]);
  git(["remote", "add", "origin", remote]);
  await writeFile(path.join(repository, "docs/package-lock.json"), "{}");
  git(["add", "."]);
  git(["commit", "-m", "Fixture with docs"]);
  git(["tag", "v0.1.0"]);
  git(["push", "origin", "main", "v0.1.0"]);
  await writeFile(
    path.join(bin, "npm"),
    `#!/usr/bin/env node
const fs = require('node:fs');
if (process.argv[2] === 'run') {
  fs.mkdirSync('dist', {recursive:true});
  fs.writeFileSync('dist/index.html', process.env.DOCS_VERSION + ' generation ' + process.env.TEST_GENERATION);
}
`,
  );
  await writeFile(
    path.join(bin, "gh"),
    `#!/usr/bin/env node
const releases = [{tag_name:'v0.1.0',draft:false,prerelease:false}];
console.log(JSON.stringify(process.argv.at(-1).endsWith('/latest') ? releases[0] : [releases]));
`,
  );
  await chmod(path.join(bin, "npm"), 0o755);
  await chmod(path.join(bin, "gh"), 0o755);
  const helper = new URL("./publish.mjs", import.meta.url);
  const invoke = (generation) =>
    execFileSync(process.execPath, [helper.pathname], {
      cwd: path.join(repository, "docs"),
      env: {
        ...process.env,
        PATH: `${bin}${path.delimiter}${process.env.PATH}`,
        GITHUB_ACTIONS: "true",
        DOCS_PUBLISH: "true",
        GITHUB_REPOSITORY: "example/docs",
        TEST_GENERATION: generation,
      },
      stdio: ["ignore", "pipe", "pipe"],
    });
  invoke("first");
  assert.equal(
    git(["show", "gh-pages:0.1.0/index.html"], remote),
    "0.1.0 generation first",
  );
  invoke("second");
  assert.equal(
    git(["show", "gh-pages:0.1.0/index.html"], remote),
    "0.1.0 generation first",
  );
  assert.equal(
    git(["show", "gh-pages:dev/index.html"], remote),
    "dev generation second",
  );
  assert.equal(
    git(["show", "gh-pages:latest/index.html"], remote),
    "latest generation second",
  );
  assert.deepEqual(
    JSON.parse(git(["show", "gh-pages:versions.json"], remote)),
    { latest: "0.1.0", versions: ["0.1.0"] },
  );
});
