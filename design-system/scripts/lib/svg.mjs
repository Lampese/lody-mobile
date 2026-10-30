// Converts Lucide's stroke-only SVGs into a list of path `d` strings so every
// platform renders identical geometry (Android ImageVector and iOS SVG assets
// are both emitted from the same paths). Lucide only uses the shapes below.

const ELEMENT = /<(path|circle|ellipse|rect|line|polyline|polygon)\b([^>]*?)\/?>/g
const ATTR = /([a-zA-Z0-9-]+)="([^"]*)"/g

const n = (v) => {
  const value = Number(v ?? 0)
  if (!Number.isFinite(value)) throw new Error(`bad number ${v}`)
  return value
}
const fmt = (v) => String(Math.round(v * 1000) / 1000)

function attrs(source) {
  const out = {}
  for (const [, key, value] of source.matchAll(ATTR)) out[key] = value
  return out
}

function points(list) {
  const nums = list.trim().split(/[\s,]+/).map(Number)
  const pairs = []
  for (let i = 0; i < nums.length; i += 2) pairs.push(`${fmt(nums[i])} ${fmt(nums[i + 1])}`)
  return pairs
}

function ellipsePath(cx, cy, rx, ry) {
  return `M${fmt(cx - rx)} ${fmt(cy)}a${fmt(rx)} ${fmt(ry)} 0 1 0 ${fmt(rx * 2)} 0a${fmt(rx)} ${fmt(ry)} 0 1 0 ${fmt(-rx * 2)} 0Z`
}

function rectPath(a) {
  const x = n(a.x), y = n(a.y), w = n(a.width), h = n(a.height)
  let rx = a.rx === undefined ? n(a.ry) : n(a.rx)
  let ry = a.ry === undefined ? rx : n(a.ry)
  rx = Math.min(rx, w / 2)
  ry = Math.min(ry, h / 2)
  if (rx === 0 || ry === 0) return `M${fmt(x)} ${fmt(y)}h${fmt(w)}v${fmt(h)}h${fmt(-w)}Z`
  const arc = (dx, dy) => `a${fmt(rx)} ${fmt(ry)} 0 0 1 ${fmt(dx)} ${fmt(dy)}`
  return [
    `M${fmt(x + rx)} ${fmt(y)}`,
    `h${fmt(w - 2 * rx)}`, arc(rx, ry),
    `v${fmt(h - 2 * ry)}`, arc(-rx, ry),
    `h${fmt(-(w - 2 * rx))}`, arc(-rx, -ry),
    `v${fmt(-(h - 2 * ry))}`, arc(rx, -ry),
    'Z',
  ].join('')
}

/** Returns the icon's path data list. Throws on anything Lucide is not expected to contain. */
export function svgToPaths(svg, label) {
  const root = /<svg\b([^>]*)>/.exec(svg)
  if (!root) throw new Error(`${label}: not an svg`)
  const rootAttrs = attrs(root[1])
  if (rootAttrs.viewBox !== '0 0 24 24') throw new Error(`${label}: expected 24x24 viewBox, got ${rootAttrs.viewBox}`)

  const paths = []
  for (const [, tag, rawAttrs] of svg.matchAll(ELEMENT)) {
    const a = attrs(rawAttrs)
    const required = { circle: ['r'], ellipse: ['rx', 'ry'], rect: ['width', 'height'], line: ['x1', 'y1', 'x2', 'y2'], polyline: ['points'], polygon: ['points'], path: ['d'] }[tag]
    const missing = required.filter((key) => a[key] === undefined)
    if (missing.length) throw new Error(`${label}: <${tag}> missing ${missing.join(', ')}`)
    if (a.fill && a.fill !== 'none') throw new Error(`${label}: filled <${tag}> is not supported`)
    switch (tag) {
      case 'path': paths.push(a.d.trim()); break
      case 'circle': paths.push(ellipsePath(n(a.cx), n(a.cy), n(a.r), n(a.r))); break
      case 'ellipse': paths.push(ellipsePath(n(a.cx), n(a.cy), n(a.rx), n(a.ry))); break
      case 'rect': paths.push(rectPath(a)); break
      case 'line': paths.push(`M${fmt(n(a.x1))} ${fmt(n(a.y1))}L${fmt(n(a.x2))} ${fmt(n(a.y2))}`); break
      case 'polyline':
      case 'polygon': {
        const [first, ...rest] = points(a.points)
        paths.push(`M${first}${rest.map((p) => `L${p}`).join('')}${tag === 'polygon' ? 'Z' : ''}`)
        break
      }
    }
  }
  const unsupported = /<(?!svg\b|\/svg\b|path\b|circle\b|ellipse\b|rect\b|line\b|polyline\b|polygon\b|!--)([a-z]+)/i.exec(svg)
  if (unsupported) throw new Error(`${label}: unsupported element <${unsupported[1]}>`)
  if (paths.length === 0) throw new Error(`${label}: no shapes`)
  return paths
}

/** Canonical stroked SVG used by iOS assets and the HTML preview. */
export function pathsToSvg(paths, strokeWidth, color = '#000000') {
  const body = paths.map((d) => `  <path d="${d}"/>`).join('\n')
  return `<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="${color}" stroke-width="${strokeWidth}" stroke-linecap="round" stroke-linejoin="round">\n${body}\n</svg>\n`
}
