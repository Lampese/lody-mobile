// Builds the full generated/ tree in memory. build.mjs writes it; check.mjs
// diffs it against disk.
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { emitKotlin } from './emit-kotlin.mjs'
import { emitPreview } from './emit-preview.mjs'
import { emitSwift } from './emit-swift.mjs'
import { svgToPaths } from './svg.mjs'
import { ROOT, loadColors, loadDimensions, loadElevation, loadIconManifest, loadPalette, loadTypography } from './tokens.mjs'

export function loadAll() {
  const dimensions = loadDimensions()
  const iconStroke = dimensions.groups.stroke.find((t) => t.name === 'icon').value
  const iconPaths = new Map()
  for (const [name, lucide] of Object.entries(loadIconManifest().icons)) {
    const svg = readFileSync(join(ROOT, 'icons/lucide', `${lucide}.svg`), 'utf8')
    iconPaths.set(name, svgToPaths(svg, `${name} (${lucide})`))
  }
  const colors = loadColors()
  return {
    palette: loadPalette().groups,
    colors,
    typography: loadTypography(),
    dimensions,
    elevation: loadElevation(colors),
    iconPaths,
    iconStroke,
  }
}

export function buildOutputs(tokens = loadAll()) {
  return {
    ...emitSwift(tokens),
    ...emitKotlin(tokens),
    'generated/preview.html': emitPreview(tokens),
  }
}
