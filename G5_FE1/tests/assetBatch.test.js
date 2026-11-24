import test from 'node:test';
import assert from 'node:assert/strict';
import {assetBatchCodes} from '../src/screens/assetBatch.js';
import {forms} from '../src/screens/definitions.js';

test('a prefix starts at 001 and an entered starting number continues with the same prefix',()=>{
 assert.deepEqual(assetBatchCodes(' BK ',3),['BK001','BK002','BK003']);
 assert.deepEqual(assetBatchCodes('BK001',3),['BK001','BK002','BK003']);
 assert.deepEqual(assetBatchCodes('kit-098',4),['kit-098','kit-099','kit-100','kit-101']);
 assert.deepEqual(assetBatchCodes('BK999',2),['BK999','BK1000']);
 assert.deepEqual(assetBatchCodes('BK0009',2),['BK0009','BK0010']);
 assert.deepEqual(assetBatchCodes('BK2026-1',2),['BK2026-001','BK2026-002']);
 assert.deepEqual(assetBatchCodes('BK9007199254740993',2),['BK9007199254740993','BK9007199254740994']);
});

test('invalid counts, empty prefixes, zero sequence numbers and overlong generated codes are rejected',()=>{
 for(const quantity of ['',0,-1,501,1.5,'abc',null,undefined])assert.equal(assetBatchCodes('BK001',quantity),null);
 for(const code of ['', '123','BK000','-001','A'.repeat(38),'A'+'9'.repeat(39)])assert.equal(assetBatchCodes(code,2),null);
 assert.equal(assetBatchCodes('BK',500).at(-1),'BK500');
 assert.deepEqual(assetBatchCodes('BK',1),['BK001']);
});

test('the add form accepts a batch without condition or status and the edit form still edits one device',()=>{
 const quantity=forms.AddAsset.find(field=>field.key==='quantity');
 assert.deepEqual([quantity.required,quantity.min,quantity.max,quantity.step],[true,1,500,1]);
 assert.ok(!forms.AddAsset.some(field=>['status','assetCondition'].includes(field.key)));
 assert.ok(forms.EditAsset.some(field=>field.key==='status'));
 assert.ok(forms.EditAsset.some(field=>field.key==='assetCondition'));
 assert.ok(!forms.EditAsset.some(field=>field.key==='quantity'));
});
