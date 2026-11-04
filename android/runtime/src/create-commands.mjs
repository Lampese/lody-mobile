import { effortsFor } from '../vendor/cloud-runtime/src/protocol/capability.ts';
// Android-only text creation adapter. Shared CRDT persistence and dispatch remain upstream.
export function createCommands(runtime, sendTurn, identity) {
 const attempted=new Set();
 const uuid=/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i;
 function options(projectId, agentConfigId) {
  const scope=identity();if(!scope?.userId)throw Error('not_connected');
  return runtime.creationOptions({workspaceId:scope.workspaceId,...(projectId?{projectId}:{}),...(typeof agentConfigId==='string'?{agentConfigId}:{})});
 }
 async function roles() {
  const scope=identity();if(!scope?.userId)throw Error('not_connected');
  return (await runtime.agentRoles({action:'list',workspaceId:scope.workspaceId,userId:scope.userId})).roles;
 }
 return {
  creationOptions:options,
  // The workspace's Agent Roles (emoji, per-machine targets and what each pins); a read.
  agentRoles:async()=>({roles:await roles()}),
  // One mention category for the composer's @ panel (shared runtime `mentionCatalog`); a read.
  async mentionCatalog(category,sessionId,cliType,agentType) {
   const scope=identity();if(!scope?.userId)throw Error('not_connected');
   if(!['file','skill','session','role','issue','pr','cmd'].includes(category))throw Error('invalid_category');
   return runtime.mentionCatalog({workspaceId:scope.workspaceId,userId:scope.userId,category,...(typeof sessionId==='string'&&sessionId?{sessionId}:{}),...(typeof cliType==='string'&&cliType?{cliType}:{}),...(typeof agentType==='string'&&agentType?{agentType}:{})});
  },
  // A text turn in an existing conversation. The stable turn id lets upstream reject a
  // duplicate; nothing here retries, and an uncertain write reports `unknown`.
  async sendText(input,signal) {
   signal?.throwIfAborted();
   if(!input || !uuid.test(input.turnId) || typeof input.sessionId!=='string' || !/^[a-zA-Z0-9_-]{1,128}$/.test(input.sessionId) || typeof input.text!=='string' || input.text.length>32000)throw Error('invalid_send');
   // Attachments are server references uploaded natively; upstream validates each block.
   const attachments=input.attachments??[];
   if(!Array.isArray(attachments) || attachments.length>16 || attachments.some(block=>!block || typeof block!=='object' || !['image','file'].includes(block.type)) || JSON.stringify(attachments).length>64000)throw Error('invalid_send');
   if(!input.text.trim() && !attachments.length)throw Error('invalid_send');
   for(const key of ['machineId','cliType','agentType'])if(typeof input[key]!=='string' || !input[key])throw Error('invalid_send');
   // The composer's run-config choice: an omitted field keeps the session's previous one; a null
   // model or effort clears it to the agent default (upstream `sendTurn`).
   const choice={};
   for(const [key,field] of [['modelId','modelId'],['modeId','modeId'],['effort','reasoningEffort'],['reasoningEffortConfigId','reasoningEffortConfigId']]) {
    if(input[key]===undefined)continue;
    if(input[key]===null){if(key==='modelId' || key==='effort')choice[field]=null;continue;}
    if(typeof input[key]!=='string' || !input[key] || input[key].length>128)throw Error('invalid_send');
    choice[field]=input[key];
   }
   // The turn's Agent Role (web session composer): an id runs as that Role, bound to the
   // conversation's own agent config; null is an explicit None; absent inherits the latest turn's.
   if(input.agentRoleId!==undefined && input.agentRoleId!==null) {
    if(typeof input.agentRoleId!=='string' || !input.agentRoleId || input.agentRoleId.length>4096 || typeof input.agentConfigId!=='string' || !input.agentConfigId)throw Error('invalid_send');
    choice.agentRoleId=input.agentRoleId;choice.agentConfigId=input.agentConfigId;
   } else if(input.agentRoleId===null)choice.agentRoleId=null;
   // Agent switches the reader changed (Plan, Fast, permission selectors), as upstream validates them.
   if(input.configOptionValues!==undefined) {
    const values=input.configOptionValues;
    if(!values || typeof values!=='object' || Array.isArray(values) || Object.keys(values).length>64 || Object.entries(values).some(([id,value])=>!id || id.length>128 || /(?:api[_-]?key|auth|bearer|credential|password|passwd|secret|token)/i.test(id) || !(typeof value==='boolean' || (typeof value==='string' && value.length>0 && value.length<=512))))throw Error('invalid_send');
    choice.configOptionValues=values;
   }
   const scope=identity();if(!scope?.userId)throw Error('not_connected');
   try {
    await runtime.ensureSession({sessionId:input.sessionId});
    signal?.throwIfAborted();
    const sent=await sendTurn({id:input.turnId,sessionId:input.sessionId,machineId:input.machineId,userId:scope.userId,text:input.text,cliType:input.cliType,agentType:input.agentType,...choice,...(attachments.length?{attachmentBlocks:attachments}:{}),...(input.resume?{resume:input.resume}:{}),...(input.queue?{queue:true}:{})});
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
   // A Role binds this computer's agent exactly (web): re-read it to freeze its revision as
   // provenance; the first turn's send re-reads it again and applies it or refuses.
   const pinned=input.configOptionValues;
   if(pinned!==undefined && (!pinned || typeof pinned!=='object' || Array.isArray(pinned) || Object.keys(pinned).length>64 || Object.entries(pinned).some(([k,v])=>!k || k.length>128 || !['string','boolean'].includes(typeof v) || (typeof v==='string' && v.length>128))))throw Error('invalid_creation');
   let role;
   if(input.agentRoleId!==undefined) {
    if(typeof input.agentRoleId!=='string' || !input.agentRoleId)throw Error('invalid_creation');
    role=(await roles()).find(r=>r.id===input.agentRoleId);
    const target=role?.targets.find(t=>t.machineId===agent.machineId);
    if(!target || target.agentConfigId!==agent.id || target.state!=='configured')throw Error('role_unavailable');
   }
   attempted.add(input.sessionId);
   try {
    const result=await runtime.createSession({workspaceId:scope.workspaceId,userId:scope.userId,sessionId:input.sessionId,machineId:agent.machineId,agentConfigId:agent.id,projectId:input.projectId,branch:input.branch,useWorktree:input.worktree===true,title:input.text.trim().slice(0,200),...(role?{agentRoleId:role.id,agentRoleRevision:role.revision}:{})});
    if(result.state!=='created')return {state:result.state==='unknown'?'unknown':'rejected',sessionId:input.sessionId};
    signal?.throwIfAborted();
    await runtime.ensureSession({sessionId:input.sessionId});
    signal?.throwIfAborted();
    const sent=await sendTurn({id:input.turnId,sessionId:input.sessionId,machineId:agent.machineId,userId:scope.userId,text:input.text,cliType:agent.cliType,agentType:agent.agentType,modelId:input.modelId,modeId:input.modeId,reasoningEffort:input.effort,reasoningEffortConfigId:capability?.reasoningEffortConfigId,...(pinned?{configOptionValues:pinned}:{}),...(role?{agentRoleId:role.id,agentConfigId:agent.id}:{})});
    return {state:sent.state,sessionId:input.sessionId,...(sent.state==='not_sent'&&sent.reason?{reason:sent.reason}:{})};
   } catch {return {state:'unknown',sessionId:input.sessionId};}
  },
 };
}
