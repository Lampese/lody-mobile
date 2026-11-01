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
