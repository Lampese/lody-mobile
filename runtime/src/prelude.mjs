import {createSender} from './transport.mjs';
const send = createSender(text => LodyHost.postMessage(text));
globalThis.webkit = {messageHandlers:{dataRuntime:{postMessage:send}}};
