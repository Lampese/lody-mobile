import test from 'node:test';
import assert from 'node:assert/strict';
import {cloudCommands} from '../src/cloud-commands.mjs';
test('read probe never enables presence or supplies identity for implicit Machine RPC',()=>{
 const calls=[];const runtime={start:(...args)=>calls.push(args),grant(){},setActive:value=>calls.push(value)};
 const access={configure(){},setActive(){}};
 const commands=cloudCommands(runtime,access);
 commands.connect('workspace','https://streams.example.org');
 assert.deepEqual(calls,[['workspace',undefined,false]]);
 assert.throws(()=>commands.connect('other','https://streams.example.org'),/already_connected/);
 assert.equal(commands.sendTurn,undefined);
});
