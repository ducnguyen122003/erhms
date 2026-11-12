import test from 'node:test';
import assert from 'node:assert/strict';
import {api} from '../src/api.js';

test('Excel downloads preserve binary contents and same-origin authentication',async context=>{
 const bytes=new Uint8Array([80,75,3,4,0,255,128]);
 context.mock.method(globalThis,'fetch',async(path,options)=>{
  assert.equal(path,'/api/workspace/admissions/import-template');
  assert.equal(options.credentials,'same-origin');
  assert.equal('responseType' in options,false);
  return new Response(bytes,{headers:{'Content-Type':'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'}});
 });
 const blob=await api('/api/workspace/admissions/import-template',{responseType:'blob'});
 assert.deepEqual(new Uint8Array(await blob.arrayBuffer()),bytes);
});

test('failed binary downloads retain structured authentication errors',async context=>{
 context.mock.method(globalThis,'fetch',async()=>Response.json({code:'SESSION_EXPIRED',message:'Vui lòng đăng nhập lại'},{status:401}));
 await assert.rejects(api('/api/workspace/admissions/import-template',{responseType:'blob'}),error=>error.status===401&&error.code==='SESSION_EXPIRED'&&error.message==='Vui lòng đăng nhập lại');
});

test('Excel upload uses CSRF and browser multipart boundary and preserves row validation errors',async context=>{
 const body=new FormData();body.append('file',new Blob(['xlsx']),'students.xlsx');
 const fields={'row.3.phone':'Dòng 3 · Số điện thoại không hợp lệ'};
 context.mock.method(globalThis,'fetch',async(path,options)=>{
  if(path==='/api/auth/csrf')return Response.json({headerName:'X-CSRF-TOKEN',token:'test-token'});
  assert.equal(options.method,'POST');
  assert.equal(options.body,body);
  assert.equal(options.headers['X-CSRF-TOKEN'],'test-token');
  assert.equal(options.headers['Content-Type'],undefined);
  return Response.json({code:'EXCEL_VALIDATION',message:'Kiểm tra lại file',fields},{status:400});
 });
 await assert.rejects(api('/api/workspace/admissions/import',{method:'POST',body}),error=>{
  assert.equal(error.status,400);assert.deepEqual(error.fields,fields);return true;
 });
});
