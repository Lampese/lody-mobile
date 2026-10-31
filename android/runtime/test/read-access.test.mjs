import test from 'node:test';
import assert from 'node:assert/strict';
import {createReadAccess} from '../src/read-access.mjs';
const config={gatewayBaseUrl:'https://streams.example.org',workspaceId:'workspace'};
test('empty long-poll responses preserve cursor headers without constructing a forbidden body',async()=>{
 const events=[];
 let canceled=0;
 for (const status of [204,205,304]) {
  // A transport can expose an empty stream even for a null-body status.
  const access=createReadAccess(async()=>({
   status,statusText:'',headers:new Headers({'Stream-Next-Offset':'42'}),
   body:new ReadableStream({cancel(){canceled++;}}),
  }),event=>events.push(event));
  access.configure(config);
  for(let i=0;i<40;i++) {
   const response=await access.fetch('https://streams.example.org/ds/lody/workspace%3Ameta?live=long-poll');
   assert.equal(response.status,status);
   assert.equal(response.headers.get('Stream-Next-Offset'),'42');
   assert.equal(response.body,null);
   assert.equal(await response.text(),'');
  }
 }
 assert.equal(canceled,120);
 assert.deepEqual(events,[]);
});
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

test('workspace session reads are allowed while other workspaces and session writes are denied',async()=>{
 const access=createReadAccess(async()=>new Response('session'));access.configure(config);
 assert.equal(await (await access.fetch('https://streams.example.org/ds/lody/workspace%3As%3Asession-1/bootstrap')).text(),'session');
 await assert.rejects(access.fetch('https://streams.example.org/ds/lody/other%3As%3Asession-1'),/network_denied/);
 await assert.rejects(access.fetch('https://streams.example.org/ds/lody/workspace%3As%3Asession-1',{method:'POST',body:'write'}),/network_denied/);
});
