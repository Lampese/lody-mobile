import { effortsFor } from '../vendor/cloud-runtime/src/protocol/capability.ts';
// Android-only text creation adapter. Shared CRDT persistence and dispatch remain upstream.
export function createCommands(runtime, sendTurn, identity) {
 const attempted=new Set();
 const uuid=/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i;
 function options(projectId, agentConfigId) {
  const scope=identity();if(!scope?.userId)throw Error('not_connected');
  return runtime.creationOptions({workspaceId:scope.workspaceId,...(projectId?{projectId}:{}),...(typeof agentConfigId==='string'?{agentConfigId}:{})});
 }
 return {
  creationOptions:options,
  // A text turn in an existing conversation. The stable turn id lets upstream reject a
  // duplicate; nothing here retries, and an uncertain write reports `unknown`.
  async sendText(input,signal) {
   signal?.throwIfAborted();
   if(!input || !uuid.test(input.turnId) || typeof input.sessionId!=='string' || !/^[a-zA-Z0-9_-]{1,128}$/.test(input.sessionId) || typeof input.text!=='string' || !input.text.trim() || input.text.length>32000)throw Error('invalid_send');
   for(const key of ['machineId','cliType','agentType'])if(typeof input[key]!=='string' || !input[key])throw Error('invalid_send');
   // The composer's explicit run-config choice; an omitted field keeps the session's previous one.
   const choice={};
   for(const [key,field] of [['modelId','modelId'],['modeId','modeId'],['effort','reasoningEffort'],['reasoningEffortConfigId','reasoningEffortConfigId']]) {
    if(input[key]===undefined || input[key]===null)continue;
    if(typeof input[key]!=='string' || !input[key] || input[key].length>128)throw Error('invalid_send');
    choice[field]=input[key];
   }
   const scope=identity();if(!scope?.userId)throw Error('not_connected');
   try {
    await runtime.ensureSession({sessionId:input.sessionId});
    signal?.throwIfAborted();
    const sent=await sendTurn({id:input.turnId,sessionId:input.sessionId,machineId:input.machineId,userId:scope.userId,text:input.text,cliType:input.cliType,agentType:input.agentType,...choice,...(input.resume?{resume:input.resume}:{}),...(input.queue?{queue:true}:{})});
    return {state:sent.state,reason:sent.reason};
   } catch {return {state:'unknown'};}
  },
  async createAndSend(input,signal) {
   signal?.throwIfAborted();
   if(!input || !uuid.test(input.sessionId) || !uuid.test(input.turnId) || typeof input.text!=='string' || !input.text.trim() || input.text.length>32000)throw Error('invalid_creation');
   if(attempted.has(input.sessionId))throw Error('already_attempted');
   const scope=identity(), available=await options(input.projectId);
   const agent=available.agents.find(a=>a.id===input.agentConfigId && a.machineId===input.machineId);
   if(!agent)throw Error('agent_unavailable');
   const capability=available.capabilities.find(c=>c.machineId===agent.machineId && c.agentConfigId===agent.id) ?? available.capabilities.find(c=>c.machineId===agent.machineId && !c.agentConfigId && c.cliType===agent.cliType && c.agentType===agent.agentType);
   if(input.modelId && !capability?.models.some(m=>m.id===input.modelId))throw Error('invalid_model');
   if(input.modeId && !capability?.modes.some(m=>m.id===input.modeId))throw Error('invalid_mode');
   if(input.effort && !effortsFor(capability, input.modelId).includes(input.effort))throw Error('invalid_effort');
   attempted.add(input.sessionId);
   try {
    const result=await runtime.createSession({workspaceId:scope.workspaceId,userId:scope.userId,sessionId:input.sessionId,machineId:agent.machineId,agentConfigId:agent.id,projectId:input.projectId,branch:input.branch,useWorktree:input.worktree===true,title:input.text.trim().slice(0,200)});
    if(result.state!=='created')return {state:result.state==='unknown'?'unknown':'rejected',sessionId:input.sessionId};
    signal?.throwIfAborted();
    await runtime.ensureSession({sessionId:input.sessionId});
    signal?.throwIfAborted();
    const sent=await sendTurn({id:input.turnId,sessionId:input.sessionId,machineId:agent.machineId,userId:scope.userId,text:input.text,cliType:agent.cliType,agentType:agent.agentType,modelId:input.modelId,modeId:input.modeId,reasoningEffort:input.effort,reasoningEffortConfigId:capability?.reasoningEffortConfigId});
    return {state:sent.state,sessionId:input.sessionId};
   } catch {return {state:'unknown',sessionId:input.sessionId};}
  },
 };
}
