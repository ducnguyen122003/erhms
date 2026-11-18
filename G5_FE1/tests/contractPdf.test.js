import test from 'node:test';
import assert from 'node:assert/strict';
import {contractPdfFilename,contractPdfPath,fetchContractPdf} from '../src/screens/contractPdf.js';

test('PDF preview and attachment use separate authenticated blob requests without caching old contract contents',async context=>{
 const bytes=new Uint8Array([37,80,68,70,45,49,46,55,0,255]),controller=new AbortController();
 const requested=[];
 context.mock.method(globalThis,'fetch',async(path,options)=>{
  requested.push(path);assert.equal(options.credentials,'same-origin');assert.equal(options.cache,'no-store');assert.equal(options.signal,controller.signal);
  assert.equal('responseType' in options,false);return new Response(bytes,{headers:{'Content-Type':'application/pdf'}});
 });
 for(const download of [false,true]){
  const blob=await fetchContractPdf('contract-id',download,controller.signal);
  assert.equal(blob.type,'application/pdf');assert.deepEqual(new Uint8Array(await blob.arrayBuffer()),bytes);
 }
 assert.deepEqual(requested,['/api/workspace/contracts/contract-id/pdf?download=false','/api/workspace/contracts/contract-id/pdf?download=true']);
});

test('incomplete PDF errors preserve field details for the edit flow and expired sessions remain distinguishable',async context=>{
 const fields={'templateData.studentBirthDate':'Nhập ngày sinh'};
 context.mock.method(globalThis,'fetch',async()=>Response.json({code:'VALIDATION',message:'Bổ sung thông tin trước khi xuất PDF',fields},{status:400}));
 await assert.rejects(fetchContractPdf('contract'),error=>error.status===400&&error.code==='VALIDATION'&&error.fields['templateData.studentBirthDate']===fields['templateData.studentBirthDate']);
 context.mock.method(globalThis,'fetch',async()=>Response.json({code:'UNAUTHORIZED',message:'Đăng nhập lại'},{status:401}));
 await assert.rejects(fetchContractPdf('contract'),error=>error.status===401);
});

test('PDF links encode identifiers and download filenames remain valid filesystem names',()=>{
 assert.equal(contractPdfPath('contract/id',false),'/api/workspace/contracts/contract%2Fid/pdf?download=false');
 assert.equal(contractPdfFilename({code:'HD/2026:001',id:'id'}),'hop-dong-HD-2026-001.pdf');
 assert.equal(contractPdfFilename({id:'contract-id'}),'hop-dong-contract-id.pdf');
});
