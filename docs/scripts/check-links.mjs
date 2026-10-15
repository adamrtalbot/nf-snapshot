import { readdir, readFile, stat } from "node:fs/promises";
import { resolve, relative } from "node:path";
import { pathToFileURL } from "node:url";

export async function checkLinks(directory, base) {
  const files = await readdir(directory, { recursive: true });
  const pages = files.filter((file) => file.endsWith(".html"));
  const contents = new Map(
    await Promise.all(
      pages.map(async (file) => [
        file,
        await readFile(resolve(directory, file), "utf8"),
      ]),
    ),
  );
  const problems = [];
  for (const [page, html] of contents) {
    const source = new URL(page, `https://docs.example${base}`);
    for (const [, raw] of html.matchAll(/\b(?:href|src)="([^"]*)"/g)) {
      const url = new URL(raw.replaceAll("&amp;", "&"), source);
      if (url.origin !== source.origin) continue;
      if (!url.pathname.startsWith(base)) {
        problems.push(`${page}: link outside site base: ${raw}`);
        continue;
      }
      const target = resolve(
        directory,
        decodeURIComponent(url.pathname.slice(base.length)),
      );
      if (relative(directory, target).startsWith("..")) {
        problems.push(`${page}: link outside output: ${raw}`);
        continue;
      }
      try {
        const info = await stat(target);
        const file = info.isDirectory()
          ? resolve(target, "index.html")
          : target;
        await stat(file);
        if (url.hash && file.endsWith(".html")) {
          const body =
            contents.get(relative(directory, file)) ||
            (await readFile(file, "utf8"));
          if (!body.includes(`id="${decodeURIComponent(url.hash.slice(1))}"`)) {
            problems.push(`${page}: missing anchor: ${raw}`);
          }
        }
      } catch {
        problems.push(`${page}: missing target: ${raw}`);
      }
    }
  }
  if (!pages.length) problems.push("No HTML pages found");
  return problems;
}

if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(resolve(process.argv[1])).href
) {
  const base = `/nf-snapshot/${process.env.DOCS_VERSION || "dev"}/`;
  const problems = await checkLinks(resolve("dist"), base);
  if (problems.length) throw new Error(problems.join("\n"));
  console.log(`Internal links and assets checked under ${base}`);
}
