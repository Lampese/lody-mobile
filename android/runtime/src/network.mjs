import {contractBridge} from './contract-bridge.mjs';
import '../vendor/cloud-runtime/src/runtime/index.ts';
import {createDispatcher,HOST_PROTOCOL,CORE_CAPABILITIES} from './dispatch.mjs';
import {createReadAccess} from './read-access.mjs';
import {createCommands} from './create-commands.mjs';
import {itemDetail,sendTurn,stopSessions} from '../vendor/cloud-runtime/src/runtime/session.ts';
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
const detail={createTab:(parentSessionId,sessionId,title)=>{const scope=identity;if(!scope)throw Error('not_connected');return runtime.createTab({workspaceId:scope.workspaceId,parentSessionId,sessionId,title});},itemDetail:(sessionId,entryId,itemId)=>itemDetail({sessionId,entryId,itemId})};
globalThis.androidDispatch=createDispatcher(core,emit,{...cloud,...createCommands(runtime,sendTurn,()=>identity),...detail});
emit({type:'hostReady',protocol:HOST_PROTOCOL,capabilities:[...CORE_CAPABILITIES,'connect','grant','watchSession','closeSession','creationOptions','createAndSend','sendText','itemDetail','createTab']});
