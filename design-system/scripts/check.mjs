// Fails when a contrast floor in tokens/contrast.json is broken, a font file is
// missing, or generated/ is stale relative to tokens/ and icons/.
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'
import { contrastRatio } from './lib/contrast.mjs'
import { buildOutputs, loadAll } from './lib/outputs.mjs'
import { ROOT, loadContrastPairs } from './lib/tokens.mjs'

const problems = []
const tokens = loadAll()

const byId = new Map(tokens.colors.map((c) => [c.id, c]))
for (const [fg, bg, min] of loadContrastPairs()) {
  for (const mode of ['light', 'dark']) {
    const f = byId.get(fg)
    const b = byId.get(bg)
    if (!f || !b) {
      problems.push(`contrast: unknown token in [${fg}, ${bg}]`)
      break
    }
    const ratio = contrastRatio(f[mode], b[mode])
    if (ratio < min) problems.push(`contrast ${mode}: ${fg} on ${bg} is ${ratio.toFixed(2)}, needs ${min}`)
  }
}

for (const family of tokens.typography.families) {
  for (const face of family.faces) {
    if (!existsSync(join(ROOT, 'fonts/font', `${face.resourceName}.ttf`))) {
      problems.push(`font: fonts/font/${face.resourceName}.ttf missing; run scripts/fetch-fonts.mjs`)
    }
  }
}

const expected = buildOutputs(tokens)
const walk = (dir) =>
  readdirSync(dir).flatMap((name) => {
    const p = join(dir, name)
    return statSync(p).isDirectory() ? walk(p) : [relative(ROOT, p)]
  })
const actual = existsSync(join(ROOT, 'generated')) ? walk(join(ROOT, 'generated')) : []
for (const rel of actual) {
  if (!(rel in expected)) problems.push(`generated: unexpected ${rel}`)
}
for (const [rel, content] of Object.entries(expected)) {
  const file = join(ROOT, rel)
  if (!existsSync(file) || readFileSync(file, 'utf8') !== content) problems.push(`generated: ${rel} is stale`)
}

if (problems.length) {
  console.error(`${problems.length} problem(s):\n  ${problems.join('\n  ')}\nRun node scripts/build.mjs after fixing tokens.`)
  process.exit(1)
}
console.log(`OK: ${tokens.colors.length} colors, ${tokens.typography.styles.length} text styles, ${tokens.iconPaths.size} icons`)
