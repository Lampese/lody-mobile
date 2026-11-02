import test from 'node:test';
import assert from 'node:assert/strict';
import {createCommands} from '../src/create-commands.mjs';
const input={sessionId:'11111111-1111-4111-8111-111111111111',turnId:'22222222-2222-4222-8222-222222222222',machineId:'m',agentConfigId:'a',text:'hello'};
function fixture(state='created') {
 const calls=[];const runtime={creationOptions:()=>({agents:[{id:'a',machineId:'m',cliType:'builtin',agentType:'codex'}],capabilities:[]}),createSession:async a=>{calls.push(['create',a]);return {state}},ensureSession:async()=>calls.push(['ensure'])};
 const c=createCommands(runtime,async a=>{calls.push(['send',a]);return {state:'accepted'}},()=>({workspaceId:'w',userId:'u'}));return {c,calls};
}
test('creation persists before first turn and supplies native identity',async()=>{const {c,calls}=fixture();assert.equal((await c.createAndSend(input)).state,'accepted');assert.deepEqual(calls.map(x=>x[0]),['create','ensure','send']);assert.equal(calls[0][1].userId,'u');assert.equal(calls[2][1].id,input.turnId);});
test('unknown creation is never sent or replayed',async()=>{const {c,calls}=fixture('unknown');assert.equal((await c.createAndSend(input)).state,'unknown');await assert.rejects(c.createAndSend(input),/already_attempted/);assert.equal(calls.length,1);});
test('invalid selection is rejected before any writes',async()=>{const {c,calls}=fixture();await assert.rejects(c.createAndSend({...input,agentConfigId:'wrong'}),/agent_unavailable/);assert.equal(calls.length,0);});
test('cancellation after creation never advances to sending',async()=>{const {c,calls}=fixture();const ctl=new AbortController();ctl.abort();await assert.rejects(c.createAndSend(input,ctl.signal));assert.equal(calls.length,0);});
test('a lost send acknowledgement is reported without replay',async()=>{
 const calls=[];const runtime={creationOptions:()=>({agents:[{id:'a',machineId:'m',cliType:'builtin',agentType:'codex'}],capabilities:[]}),createSession:async()=>({state:'created'}),ensureSession:async()=>true};
 const c=createCommands(runtime,async()=>{calls.push('send');throw Error('lost')},()=>({workspaceId:'w',userId:'u'}));
 assert.equal((await c.createAndSend(input)).state,'unknown');await assert.rejects(c.createAndSend(input),/already_attempted/);assert.equal(calls.length,1);
});
test('only advertised models can be sent',async()=>{const {c,calls}=fixture();await assert.rejects(c.createAndSend({...input,modelId:'invented'}),/invalid_model/);assert.equal(calls.length,0);});
test('the local project reference carries an explicit worktree choice',async()=>{
 const {readFile}=await import('node:fs/promises');
 const source=await readFile(new URL('../vendor/cloud-runtime/src/runtime/create-session.ts',import.meta.url),'utf8');
 const {transform}=await import('esbuild');const vm=await import('node:vm');
 const start=source.indexOf('export async function createSession(');
 const code=(await transform(source.slice(start).replace('export async function','async function'),{loader:'ts'})).code;
 for(const worktree of [false,true]) {
  let meta;
  class Flock {set(key,value){if(key[0]==='m')meta=value}commit(){}exportJson(){return {}}}
  const context={Flock,crypto,Date,TextEncoder,attempted:new Set(),quotaReason:()=>undefined,workspaceSessionCount:()=>0,encodeFrame:x=>x,clientFor:async()=>({create:async()=>({ok:true})})};
  vm.runInNewContext(code+';globalThis.create=createSession',context);
  const result=await context.create({workspaceId:'w',sessionId:input.sessionId,machineId:'m',agentConfigId:'a',userId:'u',title:'hello',projectId:'m:local:p',useWorktree:worktree},{agents:[{id:'a',machineId:'m',cliType:'builtin',agentType:'codex'}],project:{id:'m:local:p'}},{flock:{get(){},scan(){return []},importJson(){}},client:{append:async()=>({ok:true})}},()=>{});
  assert.equal(result.state,'created');assert.equal(meta.project.useWorktree===true,worktree);
 }
});
test('models from another provider of the same type cannot be used',async()=>{
 const runtime={creationOptions:()=>({agents:[{id:'a',machineId:'m',cliType:'builtin',agentType:'codex'}],capabilities:[{machineId:'m',agentConfigId:'other',cliType:'builtin',agentType:'codex',models:[{id:'other-model'}]}]})};
 const c=createCommands(runtime,()=>{},()=>({workspaceId:'w',userId:'u'}));await assert.rejects(c.createAndSend({...input,modelId:'other-model'}),/invalid_model/);
});
test('runtime projection preserves capabilities for providers of one agent type',async()=>{
 const {readFile}=await import('node:fs/promises'),{transform}=await import('esbuild'),vm=await import('node:vm');
 const source=(await readFile(new URL('../vendor/cloud-runtime/src/runtime/create-session.ts',import.meta.url),'utf8'));
 const code=(await transform(source.slice(source.indexOf('export function creationOptions('),source.indexOf('const attempted =')).replace('export function','function'),{loader:'ts'})).code;
 const context={crypto,projectRows:()=>({projects:[]}),isThoughtLevel:()=>false,validConfigValue:()=>false};vm.runInNewContext(code+';globalThis.options=creationOptions',context);
 const rows=['a','b'].map(id=>({key:['acpCapability',id],value:{cliType:'builtin',agentType:'codex',models:[{modelId:id,name:id}],fetchedAt:1}}));
 const result=context.options(undefined,{scan:()=>[]},new Map([['m',{scan:()=>rows}]]));assert.equal(result.capabilities.length,2);assert.equal(result.capabilities[0].agentConfigId,'a');assert.equal(result.capabilities[1].models[0].id,'b');
});

test('legacy capability keys preserve all builtin agent types',async()=>{
 const {readFile}=await import('node:fs/promises'),{transform}=await import('esbuild'),vm=await import('node:vm');
 const source=(await readFile(new URL('../vendor/cloud-runtime/src/runtime/create-session.ts',import.meta.url),'utf8'));
 const code=(await transform(source.slice(source.indexOf('export function creationOptions('),source.indexOf('const attempted =')).replace('export function','function'),{loader:'ts'})).code;
 const context={crypto,projectRows:()=>({projects:[]}),isThoughtLevel:()=>false,validConfigValue:()=>false};vm.runInNewContext(code+';globalThis.options=creationOptions',context);
 const rows=['grok','codex'].map(id=>({key:['acpCapability','builtin',id],value:{cliType:'builtin',agentType:id,models:[{modelId:id,name:id}],fetchedAt:1}}));
 const result=context.options(undefined,{scan:()=>[]},new Map([['m',{scan:()=>rows}]]));assert.equal(result.capabilities.length,2);assert.equal(result.capabilities[0].agentConfigId,undefined);assert.equal(result.capabilities[0].agentType,'grok');assert.equal(result.capabilities[1].models[0].id,'codex');
});

test('thought-level choices validate and send without a per-model catalog',async()=>{
 let sent;
 const capability={machineId:'m',agentConfigId:'a',models:[],reasoningEfforts:{},configOptions:[{id:'thinking',type:'select',category:'thought_level',options:[{id:'high'}]}],reasoningEffortConfigId:'thinking'};
 const runtime={creationOptions:()=>({agents:[{id:'a',machineId:'m'}],capabilities:[capability]}),createSession:async()=>({state:'created'}),ensureSession:async()=>{}};
 const commands=createCommands(runtime,async value=>{sent=value;return {state:'accepted'}},()=>({userId:'u',workspaceId:'w'}));
 await assert.rejects(commands.createAndSend({...input,effort:'invented'}),/invalid_effort/);
 assert.equal((await commands.createAndSend({...input,effort:'high'})).state,'accepted');
 assert.equal(sent.reasoningEffort,'high');assert.equal(sent.reasoningEffortConfigId,'thinking');
});
