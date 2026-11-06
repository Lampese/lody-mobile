import {contractBridge} from './contract-bridge.mjs';
import '../vendor/cloud-runtime/src/runtime/index.ts';
import {createDispatcher,HOST_PROTOCOL,CORE_CAPABILITIES} from './dispatch.mjs';
import {createReadAccess} from './read-access.mjs';
import {createCommands} from './create-commands.mjs';
import {controlTurn,itemDetail,sendTurn,stopSessions} from '../vendor/cloud-runtime/src/runtime/session.ts';
import {cloudCommands} from './cloud-commands.mjs';
const emit=message=>globalThis.webkit.messageHandlers.dataRuntime.postMessage(message);
const access=createReadAccess(globalThis.fetch.bind(globalThis),emit);
globalThis.fetch=access.fetch;
// Bundled Streams uses fetch. Other outbound APIs have no permission in this host.
globalThis.XMLHttpRequest=undefined;globalThis.WebSocket=undefined;globalThis.EventSource=undefined;
const runtime=contractBridge(globalThis.dataRuntime,emit);
const core={ping:()=>runtime.ping(),probeSchema:()=>runtime.probeSchema(),setActive:active=>{access.setActive(active);if(!active)stopSessions();return runtime.setActive(active);}};
const cloud=cloudCommands(runtime,access,stopSessions);
let identity;
const connect=cloud.connect;cloud.connect=(workspaceId,gateway,userId)=>{const result=connect(workspaceId,gateway,userId);identity={workspaceId,userId};return result;};
// A tool step's detail (command, output, diff) reads the watched session's local replica only.
const detail={
 messageAction: (kind, input) => {
  if (!identity || !input || !['edit','fork'].includes(kind) || !['read','send'].includes(input.action)) throw Error('invalid_action');
  const args={...input,workspaceId:identity.workspaceId,userId:identity.userId};
  return kind==='edit' ? runtime.editSession(args) : runtime.forkSession(args);
 },
 createTab:(parentSessionId,sessionId,title)=>{const scope=identity;if(!scope)throw Error('not_connected');return runtime.createTab({workspaceId:scope.workspaceId,parentSessionId,sessionId,title});},itemDetail:(sessionId,entryId,itemId)=>itemDetail({sessionId,entryId,itemId}),turnDiff:(sessionId,entryId,path)=>runtime.turnDiff({sessionId,entryId,path}),
 // Stops the watched session's running turn; a write, never retried.
 controlTurn:async(sessionId,machineId,turnId)=>{const result=await controlTurn({action:'stop',sessionId,machineId,turnId});return {state:result?.state??'unknown'};}};
globalThis.androidDispatch=createDispatcher(core,emit,{...cloud,...createCommands(runtime,sendTurn,()=>identity),...detail});
emit({type:'hostReady',protocol:HOST_PROTOCOL,capabilities:[...CORE_CAPABILITIES,'connect','grant','watchSession','closeSession','creationOptions','createAndSend','sendText','itemDetail','createTab','turnDiff','controlTurn','agentRoles','mentionCatalog','localProjects','shortcutBody','projectGroups','messageAction']});
