// Loads tokens/*.json into flat, fully resolved records that the platform
// emitters consume. All validation lives here so emitters can stay dumb.
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { generateMode } from './color.mjs'

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

export const MODES = ['light', 'dark']

/**
 * Generates every scale for both modes from tokens/palette.json. Returns
 * { modes: { light: { scales, keys, map }, dark: ... } } where map resolves
 * "scale.step" (and "key.<name>", "black", "white") to a hex for that mode.
 */
export function loadPalette() {
  const json = readJson('tokens/palette.json')
  const modes = {}
  for (const mode of MODES) {
    const config = json.modes[mode]
    if (!config) throw new Error(`palette: missing mode ${mode}`)
    const { scales, keys } = generateMode(config, json.brand, json.algorithm)
    const map = new Map([
      ['black', '#000000'],
      ['white', '#FFFFFF'],
    ])
    for (const [name, hex] of Object.entries(keys)) map.set(`key.${name}`, hex)
    for (const [name, steps] of Object.entries(scales)) {
      for (const [step, hex] of Object.entries(steps)) map.set(`${name}.${step}`, hex)
    }
    modes[mode] = { scales, keys, map }
  }
  return { modes }
}

/** "{accent.9}" or "{accent.9/12}" (12% alpha) or a literal #RRGGBB[AA], resolved in one mode. */
function resolveColor(value, palette, where) {
  const ref = /^\{([^}/]+)(?:\/(\d{1,3}))?\}$/.exec(value)
  if (ref) {
    const hit = palette.get(ref[1])
    if (!hit) throw new Error(`${where}: unknown palette reference ${value}`)
    if (ref[2] === undefined) return hit
    const alpha = Number(ref[2])
    if (alpha > 100) throw new Error(`${where}: alpha above 100 in ${value}`)
    return `${hit}${Math.round((alpha / 100) * 255).toString(16).toUpperCase().padStart(2, '0')}`
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
  const { modes } = loadPalette()
  const colors = []
  for (const [path, token, type] of leaves(readJson('tokens/color.json'))) {
    const id = path.join('.')
    if (type !== 'color') throw new Error(`${id}: expected $type color`)
    // A plain string applies the same scale step to both modes.
    const value = typeof token.$value === 'string' ? { light: token.$value, dark: token.$value } : token.$value
    const color = { id, name: camel(path), description: token.$description }
    for (const mode of MODES) {
      if (!value[mode]) throw new Error(`${id}: needs a ${mode} value`)
      color[mode] = resolveColor(value[mode], modes[mode].map, `${id}.${mode}`)
    }
    colors.push(color)
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

/**
 * Returns dimension groups ({ groupName: [{ name, value, description }] }, pt = dp),
 * unitless number groups (opacity), and motion (durations, easings, springs).
 */
export function loadDimensions() {
  const json = readJson('tokens/dimension.json')
  const groups = {}
  const numbers = {}
  for (const [group, node] of Object.entries(json)) {
    if (isMeta(group) || group === 'motion') continue
    const items = [...leaves(node)].map(([path, token]) => {
      if (typeof token.$value !== 'number') throw new Error(`${group}.${path.join('.')}: expected number`)
      return { name: camel(path), value: token.$value, description: token.$description }
    })
    if (node.$type === 'number') numbers[group] = items
    else if (node.$type === 'dimension') groups[group] = items
    else throw new Error(`${group}: $type must be dimension or number`)
  }
  const describe = ([path, token]) => ({ name: camel(path), description: token.$description, value: token.$value })
  const motion = {
    durations: [...leaves(json.motion.duration)].map(describe),
    easings: [...leaves(json.motion.easing)].map((entry) => {
      const t = describe(entry)
      if (!Array.isArray(t.value) || t.value.length !== 4) throw new Error(`easing ${t.name}: expected 4 numbers`)
      return t
    }),
    springs: [...leaves(json.motion.spring)].map((entry) => {
      const t = describe(entry)
      const { duration, bounce } = t.value
      if (!(duration > 0) || !(bounce >= 0 && bounce < 1)) throw new Error(`spring ${t.name}: needs duration > 0, 0 <= bounce < 1`)
      // SwiftUI's spring(duration:bounce:) mapping; Compose takes the same physics as stiffness / damping.
      t.stiffness = Math.round(((2 * Math.PI) / duration) ** 2 * 10) / 10
      t.dampingRatio = Math.round((1 - bounce) * 1000) / 1000
      return t
    }),
  }
  return { groups, numbers, motion }
}

/** Shadow levels; color is a semantic color token id so it follows light / dark. */
export function loadElevation(colors) {
  const ids = new Set(colors.map((c) => c.id))
  return [...leaves(readJson('tokens/elevation.json'))].map(([path, token]) => {
    const { y, blur, color } = token.$value
    if (!ids.has(color)) throw new Error(`elevation ${path.join('.')}: unknown color ${color}`)
    return { name: camel(path), description: token.$description, y, blur, color: camel(color.split('.')) }
  })
}

export function loadContrastPairs() {
  return readJson('tokens/contrast.json').pairs
}

export function loadIconManifest() {
  return readJson('icons/manifest.json')
}
