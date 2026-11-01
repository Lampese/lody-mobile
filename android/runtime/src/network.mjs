import '../vendor/cloud-runtime/src/runtime/index.ts';
import {createDispatcher,HOST_PROTOCOL,CORE_CAPABILITIES} from './dispatch.mjs';
import {createReadAccess} from './read-access.mjs';
import {createCommands} from './create-commands.mjs';
import {sendTurn,stopSessions} from '../vendor/cloud-runtime/src/runtime/session.ts';
import {cloudCommands} from './cloud-commands.mjs';
const emit=message=>globalThis.webkit.messageHandlers.dataRuntime.postMessage(message);
const access=createReadAccess(globalThis.fetch.bind(globalThis),emit);
globalThis.fetch=access.fetch;
// Bundled Streams uses fetch. Other outbound APIs have no permission in this host.
globalThis.XMLHttpRequest=undefined;globalThis.WebSocket=undefined;globalThis.EventSource=undefined;
const runtime=globalThis.dataRuntime;
const core={ping:()=>runtime.ping(),probeSchema:()=>runtime.probeSchema(),setActive:active=>{access.setActive(active);if(!active)stopSessions();runtime.setActive(active);return true;}};
const cloud=cloudCommands(runtime,access,stopSessions);
let identity;
const connect=cloud.connect;cloud.connect=(workspaceId,gateway,userId)=>{const result=connect(workspaceId,gateway,userId);identity={workspaceId,userId};return result;};
globalThis.androidDispatch=createDispatcher(core,emit,{...cloud,...createCommands(runtime,sendTurn,()=>identity)});
emit({type:'hostReady',protocol:HOST_PROTOCOL,capabilities:[...CORE_CAPABILITIES,'connect','grant','watchSession','closeSession','creationOptions','createAndSend']});
