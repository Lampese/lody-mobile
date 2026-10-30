// Theme generation in OKLCH (Björn Ottosson's OKLab), in the spirit of Linear's
// generated themes: the brand supplies a few hues, each mode supplies its own
// neutral endpoints and rules, and every scale is derived. Scales use
// Radix-style 12 step roles:
//   1 app background · 2 subtle background · 3 tinted fill · 4 pressed · 5 selected
//   6 subtle border · 7 border · 8 strong border · 9 key color · 10 key pressed
//   11 readable text · 12 high-contrast text
// Chromatic scales add `solid` / `solidPressed`: the key darkened just enough to
// carry white text at 4.5:1, for filled buttons.

const toLinear = (c) => (c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4)
const toGamma = (c) => (c <= 0.0031308 ? 12.92 * c : 1.055 * c ** (1 / 2.4) - 0.055)

/** Accepts #RRGGBB or the web tokens' `hsl(H S% L%)` notation; returns #RRGGBB. */
export function toHex(value) {
  if (/^#[0-9a-f]{6}$/i.test(value)) return value.toUpperCase()
  const m = /^hsl\(\s*([\d.]+)\s+([\d.]+)%\s+([\d.]+)%\s*\)$/.exec(value)
  if (!m) throw new Error(`Unsupported color ${value}; use #RRGGBB or hsl(H S% L%)`)
  const [h, s, l] = [Number(m[1]), Number(m[2]) / 100, Number(m[3]) / 100]
  const k = (n) => (n + h / 30) % 12
  const a = s * Math.min(l, 1 - l)
  const f = (n) => l - a * Math.max(-1, Math.min(k(n) - 3, 9 - k(n), 1))
  return `#${[f(0), f(8), f(4)].map((v) => Math.round(v * 255).toString(16).toUpperCase().padStart(2, '0')).join('')}`
}

function hexToLinear(hex) {
  return [1, 3, 5].map((i) => toLinear(parseInt(hex.slice(i, i + 2), 16) / 255))
}

export function hexToOklch(hex) {
  const [r, g, b] = hexToLinear(hex)
  const l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
  const m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
  const s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
  const L = 0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s
  const A = 1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s
  const B = 0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s
  return { l: L, c: Math.hypot(A, B), h: ((Math.atan2(B, A) * 180) / Math.PI + 360) % 360 }
}

function oklchToLinearRgb({ l, c, h }) {
  const A = c * Math.cos((h * Math.PI) / 180)
  const B = c * Math.sin((h * Math.PI) / 180)
  const l_ = (l + 0.3963377774 * A + 0.2158037573 * B) ** 3
  const m_ = (l - 0.1055613458 * A - 0.0638541728 * B) ** 3
  const s_ = (l - 0.0894841775 * A - 1.291485548 * B) ** 3
  return [
    4.0767416621 * l_ - 3.3077115913 * m_ + 0.2309699292 * s_,
    -1.2684380046 * l_ + 2.6097574011 * m_ - 0.3413193965 * s_,
    -0.0041960863 * l_ - 0.7034186147 * m_ + 1.707614701 * s_,
  ]
}

const inGamut = (rgb) => rgb.every((v) => v >= -1e-4 && v <= 1 + 1e-4)

/** Converts to sRGB hex, reducing chroma (keeping lightness and hue) until it fits. */
export function oklchToHex(color) {
  let rgb = oklchToLinearRgb(color)
  if (!inGamut(rgb)) {
    let lo = 0
    let hi = color.c
    for (let i = 0; i < 24; i++) {
      const mid = (lo + hi) / 2
      if (inGamut(oklchToLinearRgb({ ...color, c: mid }))) lo = mid
      else hi = mid
    }
    rgb = oklchToLinearRgb({ ...color, c: lo })
  }
  return `#${rgb
    .map((v) => Math.round(Math.min(1, Math.max(0, toGamma(Math.min(1, Math.max(0, v))))) * 255))
    .map((v) => v.toString(16).toUpperCase().padStart(2, '0'))
    .join('')}`
}

function luminance(hex) {
  const [r, g, b] = hexToLinear(hex)
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

export function contrast(a, b) {
  const [x, y] = [luminance(a), luminance(b)].sort((p, q) => q - p)
  return (x + 0.05) / (y + 0.05)
}

const lerp = (a, b, t) => a + (b - a) * t
const STEPS = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]

/** Neutral scale from the mode's background (step 1) to its foreground (step 12). */
function neutralScale(bg, fg, curve) {
  const [a, b] = [hexToOklch(bg), hexToOklch(fg)]
  const out = {}
  STEPS.forEach((step, i) => {
    out[step] =
      step === 1 ? bg : step === 12 ? fg : oklchToHex({ l: lerp(a.l, b.l, curve[i]), c: lerp(a.c, b.c, curve[i]), h: a.c > 0.002 ? a.h : b.h })
  })
  return out
}

/** Finds the lightness between `from` and `to` closest to `from` that reaches `target` contrast on `against`. */
function solveLightness(key, from, to, against, target) {
  const at = (l) => oklchToHex({ ...key, l })
  if (contrast(at(from), against) >= target) return at(from)
  let [lo, hi] = [0, 1]
  for (let i = 0; i < 30; i++) {
    const mid = (lo + hi) / 2
    if (contrast(at(lerp(from, to, mid)), against) >= target) hi = mid
    else lo = mid
  }
  return at(lerp(from, to, hi))
}

/** Chromatic scale whose step 9 is the (mode-adjusted) key color. */
function chromaticScale(keyHex, bg, fg, { tint, text, solid }) {
  const key = hexToOklch(keyHex)
  const [b, f] = [hexToOklch(bg), hexToOklch(fg)]
  const out = {}
  // 1–8: tints from the background toward the key, chroma rising with them.
  for (let i = 0; i < 8; i++) {
    out[i + 1] = oklchToHex({ l: lerp(b.l, key.l, tint.l[i]), c: key.c * tint.c[i], h: key.h })
  }
  out[9] = keyHex
  // 10: the key while pressed, one small step toward the foreground.
  out[10] = oklchToHex({ ...key, l: lerp(key.l, f.l, 0.08) })
  // 11: text, guaranteed readable on its own tinted fill (step 3) and therefore on step 1.
  out[11] = solveLightness(key, key.l, f.l, out[3], text.contrast)
  // 12: high-contrast text, close to the foreground with a trace of the hue.
  out[12] = oklchToHex({ l: lerp(key.l, f.l, 0.85), c: key.c * 0.4, h: key.h })
  // Filled controls: darken toward black until white text reaches the target.
  out.solid = solveLightness(key, key.l, 0, '#FFFFFF', solid.contrast)
  const s = hexToOklch(out.solid)
  out.solidPressed = oklchToHex({ ...s, l: s.l - 0.05 })
  return out
}

/** Applies a mode's key adjustment: dark mode lifts brand colors (same hue) like iOS system colors. */
function adjustKey(hex, adjust) {
  if (!adjust) return hex
  const k = hexToOklch(hex)
  const l = Math.max(k.l + (adjust.lift ?? 0), adjust.minL ?? 0)
  return l === k.l ? hex : oklchToHex({ ...k, l })
}

/**
 * Builds every scale for one mode from the brand hues and the mode's rules.
 * Returns { scales: { name: { step: hex } }, keys: { name: hex } }.
 */
export function generateMode(mode, brand, algorithm) {
  const keys = {
    background: toHex(mode.neutral.background),
    foreground: toHex(mode.neutral.foreground),
  }
  for (const [role, value] of Object.entries(brand)) {
    if (role.startsWith('$')) continue
    keys[role] = adjustKey(toHex(value), mode.keyAdjust)
  }
  const { background: bg, foreground: fg } = keys
  // A near-black background needs a steeper tint curve for steps 1–8 to read as tinted.
  const scaleOptions = { ...algorithm, tint: mode.tint ?? algorithm.tint }
  const scales = { neutral: neutralScale(bg, fg, mode.neutral.curve) }
  for (const [role, hex] of Object.entries(keys)) {
    if (role === 'background' || role === 'foreground') continue
    scales[role] = chromaticScale(hex, bg, fg, scaleOptions)
  }
  for (const [name, hue] of Object.entries(algorithm.categorical.hues)) {
    const key = oklchToHex({ l: mode.categorical.l, c: mode.categorical.c, h: hue })
    scales[name] = chromaticScale(key, bg, fg, scaleOptions)
  }
  return { scales, keys }
}
