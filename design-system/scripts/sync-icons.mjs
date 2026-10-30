// Vendors the Lucide SVGs listed in icons/manifest.json into icons/lucide/.
// Network is only needed here; build.mjs reads the vendored copies.
import { mkdirSync, readdirSync, rmSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { ROOT, loadIconManifest } from './lib/tokens.mjs'

const { lucideVersion, icons } = loadIconManifest()
const dir = join(ROOT, 'icons/lucide')
mkdirSync(dir, { recursive: true })

const base = `https://unpkg.com/lucide-static@${lucideVersion}`
const wanted = new Set(Object.values(icons))

async function fetchText(url) {
  const res = await fetch(url)
  if (!res.ok) throw new Error(`${url}: HTTP ${res.status}`)
  return res.text()
}

const failures = []
await Promise.all(
  [...wanted].map(async (name) => {
    try {
      writeFileSync(join(dir, `${name}.svg`), await fetchText(`${base}/icons/${name}.svg`))
    } catch (error) {
      failures.push(`${name}: ${error.message}`)
    }
  }),
)
writeFileSync(join(ROOT, 'licenses/Lucide-LICENSE.txt'), await fetchText(`${base}/LICENSE`))

for (const file of readdirSync(dir)) {
  if (file.endsWith('.svg') && !wanted.has(file.slice(0, -4))) rmSync(join(dir, file))
}

if (failures.length) {
  console.error(`Failed to fetch:\n  ${failures.join('\n  ')}`)
  process.exit(1)
}
console.log(`Vendored ${wanted.size} Lucide ${lucideVersion} icons into icons/lucide/`)
