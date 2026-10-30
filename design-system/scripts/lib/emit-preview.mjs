// Emits generated/preview.html: a static review sheet of every token in both
// modes, so design changes can be judged without building either app.
import { contrastRatio } from './contrast.mjs'
import { pathsToSvg } from './svg.mjs'

const esc = (s) => String(s ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c])
const cssHex = (hex) => (hex.length === 9 ? `#${hex.slice(1)}` : hex)
const cssVar = (tokenId) => `--${tokenId.replaceAll('.', '-')}`
const v = (tokenId) => `var(${cssVar(tokenId)})`

const LABELS = ['gray', 'red', 'orange', 'yellow', 'green', 'teal', 'blue', 'indigo', 'purple', 'pink']

// Same sample as the native previews (SampleData), pre-tokenized for syntax colors.
const CODE = [
  [['keyword', 'import'], ['punctuation', ' { '], ['variable', 'openSession'], ['punctuation', ' } '], ['keyword', 'from'], ['string', " '@lody/sdk'"]],
  [],
  [['comment', '// Retry up to 3 times']],
  [['keyword', 'const'], ['variable', ' session'], ['punctuation', ' = '], ['keyword', 'await'], ['function', ' openSession'], ['punctuation', '('], ['variable', 'id'], ['punctuation', ', { '], ['property', 'retries'], ['punctuation', ': '], ['number', '3'], ['punctuation', ', '], ['property', 'verbose'], ['punctuation', ': '], ['constant', 'true'], ['punctuation', ' })']],
  [['keyword', 'await'], ['variable', ' session'], ['punctuation', '.'], ['function', 'send'], ['punctuation', '('], ['string', "'Run the test suite'"], ['punctuation', ')']],
]

export function emitPreview({ palette, colors, typography, dimensions, elevation, iconPaths, iconStroke }) {
  const byId = new Map(colors.map((c) => [c.id, c]))
  const vars = (mode) => colors.map((c) => `${cssVar(c.id)}: ${cssHex(c[mode])};`).join(' ')
  const icon = (name) => pathsToSvg(iconPaths.get(name), iconStroke, 'currentColor')
  const style = (name) => typography.styles.find((s) => s.name === name)
  const typeCss = (s) =>
    `font-family:lody-${s.family};font-weight:${s.weight};font-size:${s.size}px;line-height:${s.lineHeight}px;letter-spacing:${s.tracking}px`

  const fontFaces = typography.families
    .flatMap((f) =>
      f.faces.map(
        (face) =>
          `@font-face { font-family: lody-${f.name}; font-weight: ${face.weight}; src: url(../fonts/font/${face.resourceName}.ttf); }`,
      ),
    )
    .join('\n')
  const textClasses = typography.styles.map((s) => `.t-${s.name} { ${typeCss(s)} }`).join('\n')

  // --- Sample screen -------------------------------------------------------
  const rows = [
    ['stateRunning', 'state.running', '同一个 worktree 多个 tabs', 'lody · Claude Code', '2m', 'indigo'],
    ['stateRunning', 'state.running', 'Migrate mobile tokens to Compose', 'lody-mobile · Codex', '5m', 'green'],
    ['stateWaiting', 'state.waiting', 'Review auth middleware changes', 'Approve 3 file edits', '12m', 'orange'],
    ['stateDone', 'state.done', '为 loro swift 添加 debug trait 的支持', 'loro-swift · PR #42', '1h', 'purple'],
    ['merge', 'state.merged', 'Fix flaky sync test', 'lody · PR #3935 merged', '3h', null],
    ['stateFailed', 'state.failed', 'worktree 环境 setup 配置', 'Exited with code 1', '4h', null],
    ['stateIdle', 'state.idle', '实现 refresh token', 'lody-ios', 'Yesterday', 'blue'],
  ]
  const mockRows = rows
    .map(
      ([glyph, color, title, sub, meta, label]) =>
        `<div class="row"><span class="g" style="color:${v(color)}">${icon(glyph)}</span>` +
        `<div class="grow"><div class="t-body clip">${esc(title)}</div><div class="t-footnote" style="color:${v('text.secondary')}">${esc(sub)}</div></div>` +
        (label ? `<span class="label t-caption" style="background:${v(`label.${label}.bg`)};color:${v(`label.${label}.fg`)}"><i style="background:${v(`label.${label}.fg`)}"></i>${label}</span>` : '') +
        `<span class="t-footnote" style="color:${v('text.tertiary')}">${meta}</span></div>`,
    )
    .join('')
  const chips = ['All', 'Running', 'Needs input', 'Done']
    .map((c, i) => `<span class="chip t-subhead ${i === 0 ? 'on' : ''}">${c}</span>`)
    .join('')
  const mock = `<div class="phone">
    <div class="nav"><span class="t-largeTitle">Sessions</span><span style="color:${v('icon.primary')}">${icon('sun')}${icon('compose')}</span></div>
    <div class="chips">${chips}</div>
    <div class="section t-subhead">Running <span style="color:${v('text.placeholder')}">2</span><span class="grow"></span><span style="color:${v('icon.tertiary')}">${icon('plus')}</span></div>
    ${mockRows}
    <div class="composer"><span style="color:${v('icon.secondary')}">${icon('attach')}</span><span class="t-callout grow" style="color:${v('text.placeholder')}">Ask Lody…</span><span class="send">${icon('send')}</span></div>
  </div>`

  // --- Components ----------------------------------------------------------
  const btn = (label, bg, fg, ic) =>
    `<span class="btn t-subhead" style="background:${bg};color:${fg}">${ic ? icon(ic) : ''}${label}</span>`
  const badge = (label, bg, fg, ic) => `<span class="badge t-caption" style="background:${v(bg)};color:${v(fg)}">${icon(ic)}${label}</span>`
  const code = CODE.map(
    (line) => `<div>${line.map(([kind, text]) => `<span style="color:${v(`syntax.${kind}`)}">${esc(text)}</span>`).join('') || '&nbsp;'}</div>`,
  ).join('')
  const diff = [
    ['hunk', '@@ -12,4 +12,5 @@ func refresh()'],
    ['ctx', '  func refresh() async throws {'],
    ['rem', '-     let token = try await api.token()'],
    ['add', '+     let token = try await api.refreshToken(current)'],
    ['add', '+     store.save(token)'],
    ['ctx', '  }'],
  ]
    .map(([k, t]) => {
      const [bg, fg] = {
        hunk: ['diff.hunkBg', 'diff.hunkText'],
        ctx: [null, 'code.text'],
        rem: ['diff.removedBg', 'diff.removedText'],
        add: ['diff.addedBg', 'diff.addedText'],
      }[k]
      return `<div style="${bg ? `background:${v(bg)};` : ''}color:${v(fg)}">${esc(t)}</div>`
    })
    .join('')
  const shadow = (e) => `0 ${e.y}px ${e.blur}px ${v(`shadow.${e.name}`)}`
  const components = `<div class="stack">
    <div class="card"><div class="cap">Buttons</div><div class="wrap">
      ${btn('Run', v('ink.default'), v('ink.on'), 'send')}
      ${btn('Cancel', v('fill.secondary'), v('text.primary'))}
      ${btn('Delete', v('danger.solid'), v('text.onDanger'), 'trash')}
      ${btn('View diff', 'transparent', v('text.link'))}
      <span style="opacity:${dimensions.numbers.opacity.find((o) => o.name === 'disabled').value}">${btn('Disabled', v('ink.default'), v('ink.on'))}</span>
    </div></div>
    <div class="card"><div class="cap">Accent (state, not fills)</div><div class="wrap">
      <span class="switch"><i></i></span><span class="t-body" style="color:${v('text.link')}">Open pull request</span>
      <span class="progress"><i></i></span>
    </div></div>
    <div class="card"><div class="cap">Status</div><div class="wrap">
      ${badge('Running', 'accent.subtle', 'accent.onSubtle', 'stateRunning')}
      ${badge('Needs input', 'warning.subtle', 'warning.onSubtle', 'stateWaiting')}
      ${badge('Done', 'success.subtle', 'success.onSubtle', 'stateDone')}
      ${badge('Failed', 'danger.subtle', 'danger.onSubtle', 'stateFailed')}
      ${badge('Synced', 'info.subtle', 'info.onSubtle', 'cloud')}
    </div></div>
    <div class="card"><div class="cap">Labels</div><div class="wrap">
      ${LABELS.map((l) => `<span class="label t-caption" style="background:${v(`label.${l}.bg`)};color:${v(`label.${l}.fg`)}"><i style="background:${v(`label.${l}.fg`)}"></i>${l}</span>`).join('')}
    </div></div>
    <div class="card"><div class="cap">Banner</div>
      <div class="banner t-footnote">${icon('alert')}<span>Agent needs approval to run <code class="inline">pnpm install</code> in lody-mobile.</span></div>
    </div>
    <div class="card"><div class="cap">Code</div><div class="code t-code">${code}</div><div class="code diff t-codeSmall">${diff}</div></div>
    <div class="card"><div class="cap">Elevation</div><div class="wrap elev">
      ${elevation.map((e) => `<div class="lift t-footnote" style="box-shadow:${shadow(e)}">${e.name}<br><span style="color:${v('text.tertiary')}">y ${e.y} · blur ${e.blur}</span></div>`).join('')}
      <div class="toast t-subhead" style="box-shadow:${shadow(elevation[1])}">${icon('check')}Session archived</div>
    </div></div>
  </div>`

  // --- Reference tables ----------------------------------------------------
  const paletteRows = (mode) =>
    Object.entries(palette[mode].scales)
      .map(
        ([name, steps]) =>
          `<div class="ramp"><code>${name}</code>${Object.entries(steps)
            .map(
              ([step, hex]) =>
                `<div class="step ${step === '9' && name !== 'neutral' ? 'anchor' : ''}" title="${name}.${step} ${hex}"><span style="background:${hex}"></span><code>${step}</code></div>`,
            )
            .join('')}</div>`,
      )
      .join('')
  const keyRows = (mode) =>
    `<div class="wrap">${Object.entries(palette[mode].keys)
      .map(([k, hex]) => `<span class="key"><span style="background:${hex}"></span><code>${k} ${hex}</code></span>`)
      .join('')}</div>`

  const colorRows = colors
    .map((c) => {
      const swatch = (mode) =>
        `<td><span class="sw" style="background:${cssHex(c[mode])}"></span><code>${c[mode]}</code></td>`
      return `<tr><td><code>${c.id}</code><div class="desc">${esc(c.description)}</div></td>${swatch('light')}${swatch('dark')}</tr>`
    })
    .join('\n')

  const typeRows = typography.styles
    .map(
      (s) =>
        `<div class="type-row"><div class="meta"><code>${s.name}</code><br>${s.size}/${s.lineHeight} · ${s.weight} · ${s.tracking}pt</div>` +
        `<div class="t-${s.name}" style="color:${v('text.primary')}">` +
        `${s.family === 'mono' ? 'const session = await lody.open(id) // 会话' : '同一个 worktree 多个 tabs · Agent finished the review'}</div></div>`,
    )
    .join('\n')

  const iconCells = [...iconPaths]
    .map(([name, paths]) => `<div class="icon">${pathsToSvg(paths, iconStroke, 'currentColor')}<code>${name}</code></div>`)
    .join('\n')

  const list = (items, unit = '') => `<div class="dims">${items.map((t) => `<div><code>${t.name}</code> ${t.value}${unit}</div>`).join('')}</div>`
  const dims = [
    ...Object.entries(dimensions.groups).map(([group, items]) => `<h3>${group}</h3>${list(items)}`),
    ...Object.entries(dimensions.numbers).map(([group, items]) => `<h3>${group}</h3>${list(items)}`),
    `<h3>motion.duration</h3>${list(dimensions.motion.durations, 'ms')}`,
    `<h3>motion.easing</h3><div class="dims">${dimensions.motion.easings.map((e) => `<div><code>${e.name}</code> cubic-bezier(${e.value.join(', ')})</div>`).join('')}</div>`,
    `<h3>motion.spring</h3><div class="dims">${dimensions.motion.springs.map((s) => `<div><code>${s.name}</code> ${s.value.duration}s · bounce ${s.value.bounce} (k ${s.stiffness}, ζ ${s.dampingRatio})</div>`).join('')}</div>`,
  ].join('')

  const contrast = (mode) =>
    `${mode}: text.primary/bg.base ${contrastRatio(byId.get('text.primary')[mode], byId.get('bg.base')[mode]).toFixed(1)}`
  const radius = (name) => dimensions.groups.radius.find((r) => r.name === name).value
  const size = (name) => dimensions.groups.size.find((r) => r.name === name).value

  return `<!doctype html>
<!-- Generated by design-system/scripts/build.mjs. Do not edit. -->
<html><head><meta charset="utf-8"><title>Lody mobile tokens</title>
<style>
${fontFaces}
${textClasses}
* { box-sizing: border-box; }
body { margin: 0; font-family: lody-sans, sans-serif; background: #ececee; color: #23252a; }
main { max-width: 1180px; margin: 0 auto; padding: 32px; }
h1 { font-size: 28px; margin: 0 0 4px; } h2 { margin: 48px 0 12px; font-size: 20px; } h3 { font-size: 13px; margin: 20px 0 8px; }
code { font-family: lody-mono, monospace; font-size: 12px; }
svg { width: 20px; height: 20px; display: block; flex: none; }
.desc { color: #6b6f76; font-size: 12px; }
.grow { flex: 1; min-width: 0; } .clip { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.modes { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.mode { min-width: 0; overflow: hidden; padding: 20px; border-radius: 14px; background: ${v('bg.grouped')}; color: ${v('text.primary')}; }
.light { ${vars('light')} } .dark { ${vars('dark')} }
.phone { width: 390px; margin: 0 auto; background: ${v('bg.base')}; border-radius: 28px; padding: 12px 0 16px; overflow: hidden; border: 1px solid ${v('border.default')}; }
.nav { display: flex; justify-content: space-between; align-items: center; padding: 8px 16px; }
.nav span:last-child { display: flex; gap: 16px; }
.chips { display: flex; gap: 8px; padding: 4px 16px 8px; }
.chip { height: 32px; padding: 0 12px; display: flex; align-items: center; border-radius: 999px; border: 1px solid ${v('border.default')}; color: ${v('text.secondary')}; }
.chip.on { background: ${v('fill.selected')}; border-color: transparent; color: ${v('text.primary')}; }
.section { display: flex; gap: 8px; align-items: center; height: 36px; padding: 0 16px; color: ${v('text.tertiary')}; }
.row { display: flex; gap: 12px; align-items: center; min-height: 60px; padding: 0 16px; position: relative; }
.row::after { content: ''; position: absolute; left: 48px; right: 0; bottom: 0; border-bottom: 0.5px solid ${v('border.subtle')}; }
.label { display: inline-flex; align-items: center; gap: 6px; height: ${size('chipSm')}px; padding: 0 8px; border-radius: ${radius('sm')}px; text-transform: capitalize; }
.label i { width: 8px; height: 8px; border-radius: 99px; }
.composer { margin: 16px 16px 0; display: flex; align-items: center; gap: 8px; min-height: 48px; padding: 0 8px 0 12px; border-radius: ${radius('xl')}px; background: ${v('bg.raised')}; border: 1px solid ${v('border.default')}; }
.send { width: 32px; height: 32px; border-radius: 999px; display: grid; place-items: center; background: ${v('ink.default')}; color: ${v('ink.on')}; }
.stack { display: flex; flex-direction: column; gap: 16px; }
.card { background: ${v('bg.raised')}; border-radius: ${radius('lg')}px; padding: 16px; display: flex; flex-direction: column; gap: 12px; }
.cap { font-size: 13px; font-weight: 500; color: ${v('text.tertiary')}; }
.wrap { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.btn { display: inline-flex; align-items: center; gap: 8px; height: 40px; padding: 0 16px; border-radius: ${radius('md')}px; }
.btn svg, .badge svg { width: 16px; height: 16px; }
.badge { display: inline-flex; align-items: center; gap: 4px; height: ${size('chipSm')}px; padding: 0 8px; border-radius: ${radius('sm')}px; }
.banner { display: flex; gap: 8px; padding: 12px; border-radius: ${radius('md')}px; background: ${v('warning.subtle')}; color: ${v('warning.onSubtle')}; }
.banner svg { color: ${v('warning.default')}; }
code.inline { background: ${v('code.inlineBg')}; color: ${v('code.text')}; padding: 1px 4px; border-radius: ${radius('xs')}px; }
.code { background: ${v('code.bg')}; border: 1px solid ${v('code.border')}; border-radius: ${radius('md')}px; padding: 12px; white-space: pre; overflow-x: auto; }
.code.diff { padding: 8px 0; } .code.diff div { padding: 0 12px; }
.elev { gap: 20px; padding: 8px 4px 12px; }
.lift { width: 104px; padding: 12px; border-radius: ${radius('md')}px; background: ${v('bg.overlay')}; }
.toast { display: flex; gap: 8px; align-items: center; padding: 10px 14px; border-radius: ${radius('md')}px; background: ${v('bg.inverse')}; color: ${v('text.inverse')}; }
.ramp { display: flex; align-items: center; gap: 4px; margin-bottom: 6px; }
.ramp > code { width: 56px; }
.step { display: flex; flex-direction: column; align-items: center; gap: 2px; }
.step span { width: 28px; height: 24px; border-radius: 6px; border: 1px solid #8882; }
.step code { font-size: 10px; color: #6b6f76; }
.step.anchor span { outline: 2px solid ${v('text.primary')}; outline-offset: 2px; }
.step code { color: ${v('text.tertiary')}; }
.ramp > code { color: ${v('text.secondary')}; }
.key { display: inline-flex; align-items: center; gap: 6px; margin: 0 8px 10px 0; }
.key span { width: 16px; height: 16px; border-radius: 4px; border: 1px solid #8884; }
.key code { color: ${v('text.secondary')}; font-size: 11px; }
.switch { width: 44px; height: 26px; border-radius: 99px; background: ${v('accent.default')}; position: relative; }
.switch i { position: absolute; right: 3px; top: 3px; width: 20px; height: 20px; border-radius: 99px; background: #fff; }
.progress { width: 120px; height: ${size('progress')}px; border-radius: 99px; background: ${v('fill.secondary')}; overflow: hidden; }
.progress i { display: block; width: 60%; height: 100%; background: ${v('accent.default')}; }
table { border-collapse: collapse; width: 100%; background: #fff; border-radius: 12px; overflow: hidden; }
td { border-bottom: 1px solid #eee; padding: 6px 10px; vertical-align: middle; }
.sw { display: inline-block; width: 28px; height: 20px; border-radius: 4px; border: 1px solid #0002; vertical-align: middle; margin-right: 8px; }
.type-row { display: grid; grid-template-columns: 140px 1fr; gap: 12px; padding: 8px 0; border-bottom: 1px solid ${v('border.subtle')}; }
.type-row .meta { font-size: 11px; color: ${v('text.tertiary')}; }
.icons { display: grid; grid-template-columns: repeat(auto-fill, minmax(84px, 1fr)); gap: 8px; color: ${v('icon.primary')}; }
.icon { display: flex; flex-direction: column; align-items: center; gap: 6px; padding: 10px 0; }
.icon code { font-size: 10px; color: ${v('text.tertiary')}; }
.dims { display: flex; flex-wrap: wrap; gap: 6px 18px; font-size: 13px; }
</style></head>
<body><main>
<h1>Lody mobile design tokens</h1>
<p class="desc">${contrast('light')} · ${contrast('dark')} · ${colors.length} colors · ${typography.styles.length} text styles · ${iconPaths.size} icons</p>

<h2>Sample screen</h2>
<div class="modes"><div class="mode light">${mock}</div><div class="mode dark">${mock}</div></div>

<h2>Components</h2>
<div class="modes"><div class="mode light">${components}</div><div class="mode dark">${components}</div></div>

<h2>Palette</h2>
<p class="desc">Generated per mode from the web theme's key colors (Lody Light / Vesper). Step 9 (outlined) is the key color itself; 11 is solved for 4.5:1 text on step 3.</p>
<div class="modes"><div class="mode light">${keyRows('light')}${paletteRows('light')}</div><div class="mode dark">${keyRows('dark')}${paletteRows('dark')}</div></div>

<h2>Type</h2>
<div class="modes"><div class="mode light">${typeRows}</div><div class="mode dark">${typeRows}</div></div>

<h2>Icons</h2>
<div class="modes"><div class="mode light"><div class="icons">${iconCells}</div></div><div class="mode dark"><div class="icons">${iconCells}</div></div></div>

<h2>Semantic colors</h2>
<table><tr><td></td><td><b>Light</b></td><td><b>Dark</b></td></tr>
${colorRows}
</table>

<h2>Dimensions (pt = dp), numbers, motion</h2>
${dims}
</main></body></html>
`
}
