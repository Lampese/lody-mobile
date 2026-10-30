// OKLCH helpers (Björn Ottosson's OKLab) used to generate chromatic ramps with
// perceptually even lightness steps, gamut-mapped into sRGB.

const toLinear = (c) => (c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4)
const toGamma = (c) => (c <= 0.0031308 ? 12.92 * c : 1.055 * c ** (1 / 2.4) - 0.055)

export function hexToOklch(hex) {
  const [r, g, b] = [1, 3, 5].map((i) => toLinear(parseInt(hex.slice(i, i + 2), 16) / 255))
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
  let { c } = color
  let rgb = oklchToLinearRgb(color)
  if (!inGamut(rgb)) {
    let lo = 0
    let hi = c
    for (let i = 0; i < 24; i++) {
      const mid = (lo + hi) / 2
      if (inGamut(oklchToLinearRgb({ ...color, c: mid }))) lo = mid
      else hi = mid
    }
    c = lo
    rgb = oklchToLinearRgb({ ...color, c })
  }
  return `#${rgb
    .map((v) => Math.round(Math.min(1, Math.max(0, toGamma(Math.min(1, Math.max(0, v))))) * 255))
    .map((v) => v.toString(16).toUpperCase().padStart(2, '0'))
    .join('')}`
}

/**
 * Builds a ramp for one hue. The anchor (a brand color) lands unchanged on the
 * step whose ramp lightness is closest to its own; every other step reuses the
 * anchor's hue and scales its chroma by the step's chroma factor.
 */
export function generateRamp(anchorHex, ramp) {
  const anchor = hexToOklch(anchorHex)
  const steps = Object.keys(ramp)
  const anchorStep = steps.reduce((best, s) =>
    Math.abs(ramp[s].l - anchor.l) < Math.abs(ramp[best].l - anchor.l) ? s : best,
  )
  const peak = anchor.c / ramp[anchorStep].c
  const out = {}
  for (const step of steps) {
    out[step] =
      step === anchorStep
        ? anchorHex.toUpperCase()
        : oklchToHex({ l: ramp[step].l, c: peak * ramp[step].c, h: anchor.h })
  }
  return { steps: out, anchorStep }
}
