// Builds takumi-skins.json, the index Takumi Launcher reads to pick the jar for an instance:
//   node scripts/release-index.mjs <jars dir> <tag> <owner/repo> > takumi-skins.json
// One entry per jar (takumi_skins-<loader>-<version>+mc<node>.jar) with the exact Minecraft versions it runs on,
// its download URL in the GitHub release and its SHA-256. Fabric jars also serve Quilt.
import { createHash } from 'node:crypto';
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';

const [dir, tag, repo] = process.argv.slice(2);
if (!dir || !tag || !repo) {
  console.error('uso: release-index.mjs <jars> <tag> <owner/repo>');
  process.exit(1);
}

// mod.mc_releases per Stonecutter node, from stonecutter.properties.toml
const releases = {};
let section = null;
for (const line of readFileSync('stonecutter.properties.toml', 'utf8').split(/\r?\n/)) {
  const header = /^\["([^"]+)"\]/.exec(line);
  if (header) section = header[1];
  const list = /^mod\.mc_releases\s*=\s*\[(.*)\]/.exec(line);
  if (section && list) releases[section] = [...list[1].matchAll(/"([^"]+)"/g)].map((m) => m[1]);
}

const JAR = /^takumi_skins-(fabric|forge|neoforge)-(.+)\+mc(.+)\.jar$/;
const jars = [];
for (const file of readdirSync(dir).sort()) {
  const match = JAR.exec(file);
  if (!match || file.endsWith('-sources.jar')) continue;
  const [, loader, version, node] = match;
  const data = readFileSync(join(dir, file));
  // Forge/NeoForge jars declare one exact Minecraft version; Fabric jars cover the node's releases.
  const minecraft = loader === 'fabric' ? (releases[node] ?? [node]) : [node];
  jars.push({
    loader,
    loaders: loader === 'fabric' ? ['fabric', 'quilt'] : [loader],
    minecraft,
    version,
    file,
    url: `https://github.com/${repo}/releases/download/${tag}/${encodeURIComponent(file)}`,
    sha256: createHash('sha256').update(data).digest('hex'),
    size: statSync(join(dir, file)).size,
  });
}
if (!jars.length) {
  console.error(`No hay jars de Takumi Skins en ${dir}`);
  process.exit(1);
}
process.stdout.write(JSON.stringify({ format: 1, mod: 'takumi_skins', tag, jars }, null, 2) + '\n');
