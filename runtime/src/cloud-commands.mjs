export function cloudCommands(runtime, access) {
 let connected=false;
 return {
  connect(workspaceId,gatewayBaseUrl) {
   if(connected)throw Error('already_connected');
   access.configure({workspaceId,gatewayBaseUrl});
   connected=true;
   // Empty user identity suppresses implicit git-state Machine RPC; no presence connection.
   runtime.start(workspaceId,undefined,false);
   return true;
  },
  grant(value) {
   if(value!==null && (!value || typeof value.token!=='string' || typeof value.gatewayBaseUrl!=='string' || !Number.isFinite(value.expiresIn) || value.expiresIn<=0))throw Error('invalid_grant');
   runtime.grant(value);return true;
  },
 };
}
