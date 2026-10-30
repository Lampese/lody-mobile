// Fails when a contrast floor or surface separation in tokens/contrast.json is
// broken, a font file is missing, or generated/ is stale relative to tokens/ and icons/.
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'
import { hexToOklch } from './lib/color.mjs'
import { composite, contrastRatio } from './lib/contrast.mjs'
import { buildOutputs, loadAll } from './lib/outputs.mjs'
import { ROOT, loadContrastRules } from './lib/tokens.mjs'

const problems = []
const tokens = loadAll()

const byId = new Map(tokens.colors.map((c) => [c.id, c]))
const { pairs, surfaces, separation } = loadContrastRules()
const isTranslucent = (hex) => hex.length === 9 && !hex.endsWith('FF')
// Translucent colors are judged on every surface they may sit on; the worst case counts.
const surfacesFor = (hex, mode) => (isTranslucent(hex) ? surfaces.map((id) => byId.get(id)[mode]) : [byId.get('bg.base')[mode]])

for (const [fg, bg, min] of pairs) {
  for (const mode of ['light', 'dark']) {
    const f = byId.get(fg)
    const b = byId.get(bg)
    if (!f || !b) {
      problems.push(`contrast: unknown token in [${fg}, ${bg}]`)
      break
    }
    const ratio = Math.min(...surfacesFor(b[mode], mode).map((s) => contrastRatio(f[mode], b[mode], s)))
    if (ratio < min) problems.push(`contrast ${mode}: ${fg} on ${bg} is ${ratio.toFixed(2)}, needs ${min}`)
  }
}

// Tinted fills must stay visible on every surface: a minimum OKLCH lightness step.
for (const id of separation.tokens) {
  const token = byId.get(id)
  if (!token) {
    problems.push(`separation: unknown token ${id}`)
    continue
  }
  for (const mode of ['light', 'dark']) {
    for (const surfaceId of surfaces) {
      const surface = byId.get(surfaceId)[mode]
      const delta = Math.abs(hexToOklch(composite(token[mode], surface)).l - hexToOklch(surface).l)
      if (delta < separation.minDeltaL) {
        problems.push(`separation ${mode}: ${id} on ${surfaceId} differs by ${delta.toFixed(3)} L, needs ${separation.minDeltaL}`)
      }
    }
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
