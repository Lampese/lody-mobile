// Loads tokens/*.json into flat, fully resolved records that the platform
// emitters consume. All validation lives here so emitters can stay dumb.
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

export const ROOT = new URL('../../', import.meta.url).pathname

const readJson = (rel) => JSON.parse(readFileSync(join(ROOT, rel), 'utf8'))

const isMeta = (key) => key.startsWith('$')

export function camel(path) {
  return path
    .map((part, i) => (i === 0 ? part : part[0].toUpperCase() + part.slice(1)))
    .join('')
}

/** Walks a DTCG-style group, yielding [path, token, inheritedType] for every leaf with $value. */
function* leaves(node, path = [], type = undefined) {
  const groupType = node.$type ?? type
  if ('$value' in node) {
    yield [path, node, groupType]
    return
  }
  for (const [key, child] of Object.entries(node)) {
    if (isMeta(key)) continue
    yield* leaves(child, [...path, key], groupType)
  }
}

const HEX = /^#([0-9a-f]{6}|[0-9a-f]{8})$/i

function loadPalette() {
  const palette = new Map()
  for (const [path, token] of leaves(readJson('tokens/palette.json'))) {
    if (!HEX.test(token.$value)) throw new Error(`palette ${path.join('.')}: bad color ${token.$value}`)
    palette.set(path.join('.'), token.$value.toUpperCase())
  }
  return palette
}

function resolveColor(value, palette, where) {
  const ref = /^\{(.+)\}$/.exec(value)
  if (ref) {
    const hit = palette.get(ref[1])
    if (!hit) throw new Error(`${where}: unknown palette reference ${value}`)
    return hit
  }
  if (!HEX.test(value)) throw new Error(`${where}: expected palette reference or hex, got ${value}`)
  return value.toUpperCase()
}

/** Normalizes #RRGGBB / #RRGGBBAA to { r, g, b, a } in 0..255. */
export function parseHex(hex) {
  const h = hex.slice(1)
  return {
    r: parseInt(h.slice(0, 2), 16),
    g: parseInt(h.slice(2, 4), 16),
    b: parseInt(h.slice(4, 6), 16),
    a: h.length === 8 ? parseInt(h.slice(6, 8), 16) : 255,
  }
}

export function loadColors() {
  const palette = loadPalette()
  const colors = []
  for (const [path, token, type] of leaves(readJson('tokens/color.json'))) {
    const id = path.join('.')
    if (type !== 'color') throw new Error(`${id}: expected $type color`)
    const { light, dark } = token.$value
    if (!light || !dark) throw new Error(`${id}: needs both light and dark values`)
    colors.push({
      id,
      name: camel(path),
      description: token.$description,
      light: resolveColor(light, palette, `${id}.light`),
      dark: resolveColor(dark, palette, `${id}.dark`),
    })
  }
  return colors
}

/**
 * Inter's dynamic tracking (https://rsms.me/inter/dynmetrics/):
 * tracking(em) = a + b * e^(c * size). Returned in pt, rounded to 0.01.
 */
export function interTracking(size) {
  const em = -0.0223 + 0.185 * Math.exp(-0.1745 * size)
  return Math.round(em * size * 100) / 100
}

const DYNAMIC_TYPE = new Set([
  'largeTitle', 'title', 'title2', 'title3', 'headline', 'body',
  'callout', 'subheadline', 'footnote', 'caption', 'caption2',
])

export function loadTypography() {
  const json = readJson('tokens/typography.json')
  const families = {}
  for (const [name, family] of Object.entries(json.fontFamily)) {
    families[name] = {
      name,
      tracking: family.trackingFormula !== 'none',
      faces: Object.entries(family.faces).map(([weight, face]) => ({
        weight: Number(weight),
        ...face,
        // Android res names allow only [a-z0-9_]: Inter-SemiBold.ttf -> lody_inter_semibold
        resourceName: `lody_${face.file.replace(/\.ttf$/, '').replace(/-/g, '_').toLowerCase()}`,
      })),
    }
  }
  const styles = []
  for (const [name, style] of Object.entries(json.textStyle)) {
    const family = families[style.family]
    if (!family) throw new Error(`textStyle.${name}: unknown family ${style.family}`)
    const face = family.faces.find((f) => f.weight === style.weight)
    if (!face) throw new Error(`textStyle.${name}: family ${style.family} has no weight ${style.weight}`)
    if (!DYNAMIC_TYPE.has(style.dynamicType)) throw new Error(`textStyle.${name}: unknown dynamicType ${style.dynamicType}`)
    if (style.lineHeight < style.size) throw new Error(`textStyle.${name}: lineHeight below size`)
    styles.push({
      name,
      description: style.$description,
      family: style.family,
      weight: style.weight,
      postScriptName: face.postScriptName,
      resourceName: face.resourceName,
      size: style.size,
      lineHeight: style.lineHeight,
      tracking: style.letterSpacing ?? (family.tracking ? interTracking(style.size) : 0),
      dynamicType: style.dynamicType,
    })
  }
  return { families: Object.values(families), styles }
}

/** Returns { groupName: [{ name, value, description }] } for every dimension group, plus motion. */
export function loadDimensions() {
  const json = readJson('tokens/dimension.json')
  const groups = {}
  for (const [group, node] of Object.entries(json)) {
    if (isMeta(group) || group === 'motion') continue
    groups[group] = [...leaves(node)].map(([path, token]) => {
      if (typeof token.$value !== 'number') throw new Error(`${group}.${path.join('.')}: expected number`)
      return { name: camel(path), value: token.$value, description: token.$description }
    })
  }
  const motion = {
    durations: [...leaves(json.motion.duration)].map(([path, token]) => ({
      name: camel(path), value: token.$value, description: token.$description,
    })),
    easings: [...leaves(json.motion.easing)].map(([path, token]) => {
      if (!Array.isArray(token.$value) || token.$value.length !== 4) throw new Error(`easing ${path}: expected 4 numbers`)
      return { name: camel(path), value: token.$value }
    }),
  }
  return { groups, motion }
}

export function loadContrastPairs() {
  return readJson('tokens/contrast.json').pairs
}

export function loadIconManifest() {
  return readJson('icons/manifest.json')
}
