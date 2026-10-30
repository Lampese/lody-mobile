// Vendors the font files named in tokens/typography.json into fonts/font/ under
// their Android resource names (iOS loads them by PostScript name, so one copy
// serves both). Requires `unzip`. Network is only needed here.
import { execFileSync } from 'node:child_process'
import { copyFileSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { ROOT, loadTypography } from './lib/tokens.mjs'

const SOURCES = {
  sans: {
    url: 'https://github.com/rsms/inter/releases/download/v4.1/Inter-4.1.zip',
    fontDir: 'extras/ttf',
    license: ['LICENSE.txt', 'Inter-OFL.txt'],
  },
  mono: {
    url: 'https://github.com/JetBrains/JetBrainsMono/releases/download/v2.304/JetBrainsMono-2.304.zip',
    fontDir: 'fonts/ttf',
    license: ['OFL.txt', 'JetBrainsMono-OFL.txt'],
  },
}

const { families } = loadTypography()
const work = mkdtempSync(join(tmpdir(), 'lody-fonts-'))
try {
  for (const family of families) {
    const source = SOURCES[family.name]
    if (!source) throw new Error(`No download source for font family ${family.name}`)
    const res = await fetch(source.url)
    if (!res.ok) throw new Error(`${source.url}: HTTP ${res.status}`)
    const zip = join(work, `${family.name}.zip`)
    const out = join(work, family.name)
    writeFileSync(zip, Buffer.from(await res.arrayBuffer()))
    const members = [...family.faces.map((f) => `${source.fontDir}/${f.file}`), source.license[0]]
    execFileSync('unzip', ['-q', '-o', zip, ...members, '-d', out])
    for (const face of family.faces) {
      copyFileSync(join(out, source.fontDir, face.file), join(ROOT, 'fonts/font', `${face.resourceName}.ttf`))
    }
    copyFileSync(join(out, source.license[0]), join(ROOT, 'licenses', source.license[1]))
    console.log(`Vendored ${family.faces.length} ${family.name} faces`)
  }
} finally {
  rmSync(work, { recursive: true, force: true })
}
