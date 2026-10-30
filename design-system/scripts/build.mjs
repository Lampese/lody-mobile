// Regenerates generated/ from tokens/ and icons/. Offline; see README.md.
import { mkdirSync, rmSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { buildOutputs } from './lib/outputs.mjs'
import { ROOT } from './lib/tokens.mjs'

const outputs = buildOutputs()
rmSync(join(ROOT, 'generated'), { recursive: true, force: true })
for (const [rel, content] of Object.entries(outputs)) {
  const file = join(ROOT, rel)
  mkdirSync(dirname(file), { recursive: true })
  writeFileSync(file, content)
}
console.log(`Wrote ${Object.keys(outputs).length} files to generated/`)
