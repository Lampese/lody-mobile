// Home's "My tasks" needs each session's owner. Upstream `packages/cloud-runtime` now projects
// session meta `userId` into the catalog; this snapshot predates it. Apply the same one-line
// projection at bundle time, without modifying the pinned vendor snapshot, and fail closed if
// an upstream update moves the anchor (then re-snapshot instead and drop this adapter).
export function adaptCatalogOwner(source) {
 const anchor = '      pinned: value.isPinned === true,\n';
 if (source.includes('userId: text(value.userId)')) return source;
 if (source.split(anchor).length !== 2) throw Error('Review upstream catalog session projection');
 return source.replace(anchor, `${anchor}      userId: text(value.userId) || undefined,\n`);
}

// The new-session computer list shows each computer's OS icon, from machine meta `os`, the way
// upstream `packages/cloud-runtime` now projects it as `machineOs`. Same bundle-time rule:
// never edit the snapshot, and fail closed if an anchor moves.
export function adaptCatalogMachineOs(source) {
 if (source.includes('machineOs')) return source;
 const declare = '    machineNames: Record<string, string> = {},\n';
 const assign = '      if (name) machineNames[machineId] = name;\n';
 const result = '    machineNames,\n    machineSimulators,\n';
 for (const anchor of [declare, assign, result])
  if (source.split(anchor).length !== 2) throw Error('Review upstream catalog machine projection');
 return source
  .replace(declare, `${declare}    machineOs: Record<string, string> = {},\n`)
  .replace(assign, `${assign}      if (text(value.os)) machineOs[machineId] = text(value.os);\n`)
  .replace(result, '    machineNames,\n    machineOs,\n    machineSimulators,\n');
}
