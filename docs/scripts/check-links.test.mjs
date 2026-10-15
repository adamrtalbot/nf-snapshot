import assert from "node:assert/strict";
import { mkdtemp, mkdir, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";
import { checkLinks } from "./check-links.mjs";

test("checks base-prefixed links, relative links, anchors, and assets", async (t) => {
  const directory = await mkdtemp(join(tmpdir(), "nf-snapshot-links-"));
  t.after(() => rm(directory, { recursive: true, force: true }));
  await mkdir(join(directory, "guide"));
  await writeFile(
    join(directory, "index.html"),
    '<a href="/nf-snapshot/dev/guide/#setup">Guide</a><img src="logo.svg"><a href="https://example.com">External</a>',
  );
  await writeFile(
    join(directory, "guide/index.html"),
    '<h1 id="setup">Setup</h1><a href="../">Home</a>',
  );
  await writeFile(join(directory, "logo.svg"), "<svg/>");
  assert.deepEqual(await checkLinks(directory, "/nf-snapshot/dev/"), []);

  await writeFile(
    join(directory, "index.html"),
    '<a href="/guide/">Wrong base</a><a href="guide/#absent">Missing anchor</a><img src="missing.svg">',
  );
  const problems = await checkLinks(directory, "/nf-snapshot/dev/");
  assert.equal(problems.length, 3);
  assert.match(problems.join("\n"), /outside site base/);
  assert.match(problems.join("\n"), /missing anchor/);
  assert.match(problems.join("\n"), /missing target/);
});
