// Android's read adapter already bounds each response to 16 MiB. Allow the same
// bounded input budget per catalog replica, including bootstrap and live deltas.
export const CATALOG_INPUT_BYTES = 16 * 1024 * 1024;

// Apply the Android policy at bundle time, without modifying the pinned vendor
// snapshot. Fail closed if an upstream update changes the three budget guards.
export function adaptCatalogBudget(source) {
 const guard = "if (size > 8 * 1024 * 1024) throw new Error('catalog_limit');";
 if (source.split(guard).length !== 4) throw Error('Review upstream catalog budget guards');
 return source.replaceAll(guard, `if (size > ${CATALOG_INPUT_BYTES}) throw new Error('catalog_limit');`);
}
