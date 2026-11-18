import test from 'node:test';
import assert from 'node:assert/strict';
import {admissionStatus,documentCompleteness,documentGroups,emptyDocumentUploads,groupedProfileDocuments,maxDocumentFileBytes,pdfSelectionError,uploadedFileCount,validateDocumentUploads} from '../src/screens/admissionDocuments.js';

test('uploaded PDFs automatically include their category without a separate selection flag',()=>{
 const uploads=emptyDocumentUploads();assert.ok(validateDocumentUploads(uploads).documents);
 assert.deepEqual(documentGroups(uploads),[]);assert.equal(uploadedFileCount(uploads),0);
 uploads.IDENTITY_CARD.files=[{id:'front'},{id:'back'}];
 assert.deepEqual(validateDocumentUploads(uploads),{});assert.deepEqual(documentGroups(uploads),[{type:'IDENTITY_CARD',files:['front','back']}]);
 assert.equal(uploadedFileCount(uploads),2);
});

test('removing the last PDF automatically excludes its category while other uploads remain',()=>{
 const uploads=emptyDocumentUploads();
 uploads.IDENTITY_CARD.files=[{id:'cccd-front'},{id:'cccd-back'}];
 uploads.BIRTH_CERTIFICATE.files=[{id:'birth'}];
 assert.deepEqual(documentGroups(uploads),[{type:'IDENTITY_CARD',files:['cccd-front','cccd-back']},{type:'BIRTH_CERTIFICATE',files:['birth']}]);
 assert.equal(uploadedFileCount(uploads),3);
 uploads.IDENTITY_CARD.files.pop();assert.equal(uploadedFileCount(uploads),2);assert.equal(documentGroups(uploads)[0].files.length,1);
 uploads.IDENTITY_CARD.files.pop();assert.equal(uploadedFileCount(uploads),1);
 assert.deepEqual(documentGroups(uploads),[{type:'BIRTH_CERTIFICATE',files:['birth']}]);assert.deepEqual(validateDocumentUploads(uploads),{});
 uploads.BIRTH_CERTIFICATE.files=[];assert.ok(validateDocumentUploads(uploads).documents);
});

test('PDF selection accepts multiple PDFs and rejects images empty files oversized files and excessive counts',()=>{
 assert.equal(pdfSelectionError([{name:'front.pdf',size:100},{name:'back.PDF',size:maxDocumentFileBytes}],0),'');
 assert.ok(pdfSelectionError([{name:'photo.png',size:100}],0));assert.ok(pdfSelectionError([{name:'empty.pdf',size:0}],0));
 assert.ok(pdfSelectionError([{name:'large.pdf',size:maxDocumentFileBytes+1}],0));assert.ok(pdfSelectionError([{name:'one.pdf',size:100}],50));
 const uploads=emptyDocumentUploads();uploads.IDENTITY_CARD.files=Array.from({length:51},(_,i)=>({id:String(i)}));
 assert.ok(validateDocumentUploads(uploads).documents);
});

test('read-only documents remain categorized and older unclassified scans remain downloadable',()=>{
 const docs=[{documentType:'BIRTH_CERTIFICATE',fileId:'birth',filename:'birth.pdf'},{documentType:'IDENTITY_CARD',fileId:'front',filename:'front.pdf'},{documentType:'IDENTITY_CARD',fileId:'back',filename:'back.pdf'}];
 const groups=groupedProfileDocuments(docs);assert.equal(groups[0].label,'CCCD');assert.equal(groups[0].files.length,2);assert.equal(groups[1].label,'Giấy khai sinh bản sao');
 const old=groupedProfileDocuments([],'file:12345678-1234-1234-1234-123456789012:old.png');
 assert.equal(old[0].code,'LEGACY_SCAN');assert.equal(old[0].files[0].filename,'old.png');assert.deepEqual(groupedProfileDocuments(),[]);
});

test('completeness requires all five document types and never counts duplicate PDFs or legacy scans as missing types',()=>{
 const documents=[{documentType:'IDENTITY_CARD'},{documentType:'IDENTITY_CARD'},{documentType:'LEGACY_SCAN'}];
 assert.equal(documentCompleteness(documents).received,1);assert.equal(documentCompleteness(documents).complete,false);
 assert.deepEqual(documentCompleteness(documents).missing.map(type=>type.code),['CURRICULUM_VITAE','BIRTH_CERTIFICATE','ID_PHOTO','APPLICATION_FORM']);
 documents.push(...documentCompleteness(documents).missing.map(type=>({documentType:type.code})));
 assert.equal(documentCompleteness(documents).complete,true);assert.deepEqual(documentCompleteness(documents).missing,[]);
 assert.equal(admissionStatus({status:'PROFILE_PENDING',documentStatus:'DOCUMENTS_COMPLETE'}),'DOCUMENTS_COMPLETE');
 assert.equal(admissionStatus({status:'CONTRACTED',documentStatus:'DOCUMENTS_INCOMPLETE'}),'CONTRACTED');
});

test('editing starts with existing categorized and legacy files and permits explicitly clearing the document set',()=>{
 const uploads=emptyDocumentUploads([{documentType:'IDENTITY_CARD',fileId:'cccd',filename:'cccd.pdf'},{documentType:'LEGACY_SCAN',fileId:'old',filename:'old.png'}]);
 assert.deepEqual(documentGroups(uploads),[{type:'IDENTITY_CARD',files:['cccd']},{type:'LEGACY_SCAN',files:['old']}]);
 assert.equal(uploads.IDENTITY_CARD.files[0].filename,'cccd.pdf');assert.equal(uploadedFileCount(uploads),2);
 assert.deepEqual(validateDocumentUploads(emptyDocumentUploads(),{allowEmpty:true}),{});
 assert.ok(validateDocumentUploads(emptyDocumentUploads()).documents);
});
