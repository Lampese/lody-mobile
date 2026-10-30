import { parseHex } from './tokens.mjs'

const channel = (v) => {
  const c = v / 255
  return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4
}

const luminance = ({ r, g, b }) => 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)

/** Composites a translucent foreground over an opaque background. */
function over(fg, bg) {
  const a = fg.a / 255
  return { r: fg.r * a + bg.r * (1 - a), g: fg.g * a + bg.g * (1 - a), b: fg.b * a + bg.b * (1 - a) }
}

const hex = ({ r, g, b }) => `#${[r, g, b].map((v) => Math.round(v).toString(16).toUpperCase().padStart(2, '0')).join('')}`

/** The opaque color a (possibly translucent) color shows when drawn on `surfaceHex`. */
export function composite(colorHex, surfaceHex) {
  const color = parseHex(colorHex)
  return color.a < 255 ? hex(over(color, parseHex(surfaceHex))) : colorHex.slice(0, 7)
}

/**
 * WCAG 2.x contrast ratio. A translucent background is first composited onto
 * `surfaceHex` (what it sits on, e.g. bg.raised); a translucent foreground is
 * then composited onto that background.
 */
export function contrastRatio(fgHex, bgHex, surfaceHex = '#FFFFFF') {
  const bg = parseHex(composite(bgHex, surfaceHex))
  const l1 = luminance(over(parseHex(fgHex), bg))
  const l2 = luminance(bg)
  return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05)
}
