import '../vendor/cloud-runtime/src/runtime/index.ts';
import { createDispatcher, HOST_PROTOCOL, CORE_CAPABILITIES } from './dispatch.mjs';
const emit = message => globalThis.webkit.messageHandlers.dataRuntime.postMessage(message);
globalThis.androidDispatch = createDispatcher(globalThis.dataRuntime, emit);
emit({type:'hostReady', protocol:HOST_PROTOCOL, capabilities:CORE_CAPABILITIES});
