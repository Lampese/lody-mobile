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

/** WCAG 2.x contrast ratio. Translucent colors are composited onto the background first. */
export function contrastRatio(fgHex, bgHex) {
  const bg = parseHex(bgHex)
  const l1 = luminance(over(parseHex(fgHex), bg))
  const l2 = luminance(bg)
  return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05)
}
