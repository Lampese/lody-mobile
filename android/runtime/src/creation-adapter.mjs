/** Keep the vendored snapshot intact; match the current public local-project protocol. */
export function adaptCreationRuntime(source) {
 const anchor="kind: 'local',\n      localProjectId:";
 if(source.split(anchor).length!==2)throw Error('create_worktree_adapter_drift');
 const key='const key = `${machineId}:${cliType}:${agentType}`;';
 const capability='capabilities.set(key, {\n          machineId,';
 if(!source.includes(key) || !source.includes(capability))throw Error('creation_capability_adapter_drift');
 source=source.replace(key, "const configId = typeof row.key[1] === 'string' ? row.key[1] : '';\n        const key = configId ? `${machineId}:config:${configId}` : `${machineId}:${cliType}:${agentType}`;")
  .replace(capability,capability+"\n          ...(configId ? {agentConfigId:configId} : {}),");
 return source.replace(anchor,"kind: 'local',\n      useWorktree: args.useWorktree === true,\n      localProjectId:");
}
