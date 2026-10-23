import { execFileSync } from "node:child_process";
import {
  cp,
  lstat,
  mkdir,
  mkdtemp,
  readdir,
  rm,
  writeFile,
} from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const versionPattern = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/;
const run = (command, args, options = {}) =>
  execFileSync(command, args, { encoding: "utf8", ...options })?.trim() ?? "";

export function releaseVersion(tag) {
  const version = typeof tag === "string" ? tag.replace(/^v/, "") : "";
  if (!versionPattern.test(version))
    throw new Error(`Invalid stable release tag: ${tag}`);
  return version;
}

function versionDirectory(root, version) {
  if (version !== "dev" && version !== "latest") releaseVersion(version);
  return path.join(root, version);
}

async function exists(filename) {
  try {
    await lstat(filename);
    return true;
  } catch (error) {
    if (error.code === "ENOENT") return false;
    throw error;
  }
}

async function checkSite(directory) {
  if (!(await lstat(directory)).isDirectory())
    throw new Error(`Site must be a directory: ${directory}`);
  if (!(await exists(path.join(directory, "index.html"))))
    throw new Error(`Build has no index.html: ${directory}`);
  await checkLinks(directory);
}

async function checkLinks(directory) {
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    if (entry.isSymbolicLink())
      throw new Error(`Site contains a symbolic link: ${entry.name}`);
    if (entry.isDirectory()) await checkLinks(path.join(directory, entry.name));
  }
}

const redirect = (destination) =>
  `<!doctype html><html lang="en"><meta charset="utf-8"><title>nf-snapshot documentation</title><meta http-equiv="refresh" content="0;url=${destination}"><link rel="canonical" href="${destination}"><a href="${destination}">Open documentation</a></html>\n`;

/** Assemble locally; callers persist or deploy only after every build succeeds. */
export async function assembleSite({
  archive,
  output,
  releases,
  latestTag,
  build,
}) {
  if (path.resolve(archive) === path.resolve(output))
    throw new Error("Archive and output must be different directories");
  const stable = releases
    .filter((release) => !release.draft && !release.prerelease)
    .map((release) => ({
      ...release,
      version: releaseVersion(release.tag_name),
    }));
  const latest = latestTag ? releaseVersion(latestTag) : null;
  await mkdir(output, { recursive: true });
  const versions = new Set();
  if (await exists(archive)) {
    for (const entry of await readdir(archive, { withFileTypes: true })) {
      if (!versionPattern.test(entry.name)) continue;
      const source = versionDirectory(archive, entry.name);
      await checkSite(source);
      await cp(source, versionDirectory(output, entry.name), {
        recursive: true,
      });
      versions.add(entry.name);
    }
  }
  const buildVersion = async (version, tag) => {
    const destination = versionDirectory(output, version);
    await rm(destination, { recursive: true, force: true });
    await build({ version, tag, destination });
    await checkSite(destination);
  };
  await buildVersion("dev", null);
  for (const release of stable) {
    if (!versions.has(release.version)) {
      await buildVersion(release.version, release.tag_name);
      versions.add(release.version);
    }
  }
  if (latest && !versions.has(latest))
    throw new Error(`Latest release has no documentation: ${latestTag}`);
  if (latest) await buildVersion("latest", latestTag);
  else {
    await mkdir(versionDirectory(output, "latest"), { recursive: true });
    await writeFile(
      path.join(output, "latest/index.html"),
      redirect("../dev/"),
    );
  }
  const ordered = [...versions].sort((a, b) => {
    const left = a.split(".").map(BigInt);
    const right = b.split(".").map(BigInt);
    for (let index = 0; index < 3; index++) {
      if (left[index] !== right[index])
        return left[index] > right[index] ? -1 : 1;
    }
    return 0;
  });
  const manifest = { latest, versions: ordered };
  await writeFile(
    path.join(output, "versions.json"),
    `${JSON.stringify(manifest, null, 2)}\n`,
  );
  await writeFile(
    path.join(output, "index.html"),
    redirect(latest ? "./latest/" : "./dev/"),
  );
  await writeFile(path.join(output, ".nojekyll"), "");
  return manifest;
}

async function publish() {
  if (
    process.env.GITHUB_ACTIONS !== "true" ||
    process.env.DOCS_PUBLISH !== "true"
  ) {
    throw new Error(
      "Publishing runs only in GitHub Actions with DOCS_PUBLISH=true",
    );
  }
  const repository = process.env.GITHUB_REPOSITORY;
  if (!/^[\w.-]+\/[\w.-]+$/.test(repository ?? ""))
    throw new Error("Invalid GITHUB_REPOSITORY");
  const root = run("git", ["rev-parse", "--show-toplevel"]);
  const temporary = await mkdtemp(path.join(tmpdir(), "nf-snapshot-docs-"));
  const archive = path.join(temporary, "archive");
  const output = path.join(root, "docs/.site");
  try {
    // Read the archive inside the workflow's deployment concurrency lock.
    const branch = run(
      "git",
      ["ls-remote", "--heads", "origin", "refs/heads/gh-pages"],
      { cwd: root },
    );
    if (branch) {
      run(
        "git",
        ["-c", "http.version=HTTP/1.1", "fetch", "origin", "gh-pages"],
        { cwd: root },
      );
      run("git", ["worktree", "add", "--detach", archive, "FETCH_HEAD"], {
        cwd: root,
      });
    } else {
      run("git", ["worktree", "add", "--detach", archive, "HEAD"], {
        cwd: root,
      });
      run("git", ["switch", "--orphan", "docs-archive"], { cwd: archive });
    }
    const all = JSON.parse(
      run("gh", [
        "api",
        "--paginate",
        "--slurp",
        `repos/${repository}/releases?per_page=100`,
      ]),
    ).flat();
    const stable = all.filter(
      (release) => !release.draft && !release.prerelease,
    );
    const supported = [];
    for (const release of stable) {
      try {
        releaseVersion(release.tag_name);
      } catch {
        console.warn(`Skipping non-numeric release ${release.tag_name}`);
        continue;
      }
      run(
        "git",
        [
          "-c",
          "http.version=HTTP/1.1",
          "fetch",
          "origin",
          `refs/tags/${release.tag_name}:refs/tags/${release.tag_name}`,
        ],
        { cwd: root },
      );
      const files = run(
        "git",
        ["ls-tree", "--name-only", release.tag_name, "docs/package-lock.json"],
        { cwd: root },
      );
      if (files) supported.push(release);
      else
        console.warn(
          `Skipping ${release.tag_name}: this tag predates the documentation site`,
        );
    }
    let latestTag = null;
    if (stable.length) {
      const latest = JSON.parse(
        run("gh", ["api", `repos/${repository}/releases/latest`]),
      );
      if (supported.some((release) => release.tag_name === latest.tag_name))
        latestTag = latest.tag_name;
    }
    await rm(output, { recursive: true, force: true });
    await assembleSite({
      archive,
      output,
      releases: supported,
      latestTag,
      build: async ({ version, tag, destination }) => {
        const source = tag ? path.join(temporary, `source-${version}`) : root;
        if (tag)
          run("git", ["worktree", "add", "--detach", source, tag], {
            cwd: root,
          });
        try {
          const cwd = path.join(source, "docs");
          run("npm", ["ci"], { cwd, stdio: "inherit" });
          run("npm", ["run", "build"], {
            cwd,
            stdio: "inherit",
            env: { ...process.env, DOCS_VERSION: version },
          });
          await cp(path.join(cwd, "dist"), destination, { recursive: true });
        } finally {
          if (tag)
            run("git", ["worktree", "remove", "--force", source], {
              cwd: root,
            });
        }
      },
    });
    for (const name of await readdir(archive)) {
      if (name !== ".git")
        await rm(path.join(archive, name), { recursive: true, force: true });
    }
    await cp(output, archive, { recursive: true });
    run("git", ["add", "--all"], { cwd: archive });
    if (run("git", ["status", "--porcelain"], { cwd: archive })) {
      run(
        "git",
        [
          "-c",
          "user.name=github-actions[bot]",
          "-c",
          "user.email=github-actions[bot]@users.noreply.github.com",
          "commit",
          "-m",
          "Update documentation archive",
        ],
        { cwd: archive },
      );
      const commit = run("git", ["rev-parse", "HEAD"], { cwd: archive });
      run("git", ["push", "origin", `${commit}:refs/heads/gh-pages`], {
        cwd: root,
        stdio: "inherit",
      });
    }
  } finally {
    if (await exists(path.join(archive, ".git")))
      run("git", ["worktree", "remove", "--force", archive], { cwd: root });
    await rm(temporary, { recursive: true, force: true });
  }
}

if (
  process.argv[1] &&
  path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)
) {
  await publish();
}
