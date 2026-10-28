import test from 'node:test';
import assert from 'node:assert/strict';
import {createReadAccess} from '../src/read-access.mjs';
const config={gatewayBaseUrl:'https://streams.example.org',workspaceId:'workspace'};
test('only scoped catalog reads reach fetch, with cookies and redirects disabled',async()=>{
 const calls=[];const access=createReadAccess(async(request)=>{calls.push(request);return new Response('ok')});
 access.configure(config);
 const result=await access.fetch('https://streams.example.org/ds/lody/workspace%3Ameta/bootstrap');
 assert.equal(await result.text(),'ok');assert.equal(calls[0].redirect,'error');assert.equal(calls[0].credentials,'omit');
 for (const url of ['https://evil.example.org/ds/lody/workspace%3Ameta','https://streams.example.org/ds/lody/other%3Ameta','https://streams.example.org/ds/lody/workspace%3Arpc','https://streams.example.org/script.js']) {
  await assert.rejects(access.fetch(url),/network_denied/);
 }
 await assert.rejects(access.fetch('https://streams.example.org/ds/lody/workspace%3Ameta',{method:'POST',body:'write'}),/network_denied/);
 assert.equal(calls.length,1);
});
test('background aborts active response bodies and defers new requests until foreground',async()=>{
 let captured, calls=0;
 const access=createReadAccess(async request=>{captured=request;calls++;return new Response('ok')});access.configure(config);
 const response=await access.fetch('https://streams.example.org/ds/lody/workspace%3Ameta');
 access.setActive(false);assert.equal(captured.signal.aborted,true);
 const next=access.fetch('https://streams.example.org/ds/lody/workspace%3Ameta');
 await Promise.resolve();assert.equal(calls,1);
 access.setActive(true);await (await next).text();assert.equal(calls,2);
 await response.body.cancel();
});
