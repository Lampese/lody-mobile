export function cloudCommands(runtime, access, stopSessions) {
 let connected=false, selected;
 return {
  connect(workspaceId,gatewayBaseUrl) {
   if(connected)throw Error('already_connected');
   access.configure({workspaceId,gatewayBaseUrl});
   connected=true;
   // Empty user identity suppresses implicit git-state Machine RPC; no presence connection.
   runtime.start(workspaceId,undefined,false);
   return true;
  },
  watchSession(id) {
   if(!connected)throw Error('not_connected');
   if(typeof id!=='string' || !/^[a-zA-Z0-9_-]{1,128}$/.test(id))throw Error('invalid_session');
   stopSessions();selected=id;
   return runtime.session(id);
  },
  closeSession(id) {
   if(selected===id){stopSessions();selected=undefined;}
   return true;
  },
  grant(value) {
   if(value!==null && (!value || typeof value.token!=='string' || typeof value.gatewayBaseUrl!=='string' || !Number.isFinite(value.expiresIn) || value.expiresIn<=0))throw Error('invalid_grant');
   runtime.grant(value);return true;
  },
 };
}
