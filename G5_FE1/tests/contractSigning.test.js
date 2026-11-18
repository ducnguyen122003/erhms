import test from 'node:test';
import assert from 'node:assert/strict';
import {canProvisionContract,canReviewSignedScan,canSendContract,canSubmitSignedScan,signingReviewFields,validateSignedScan} from '../src/screens/contractSigning.js';
import {canScreen,screenNavigation,screenPath,screenRoute} from '../src/screens/access.js';
import {forms} from '../src/screens/definitions.js';

test('contract actions follow the paper signing sequence and approval gates account provisioning',()=>{
 const states=['DRAFT','SENT_FOR_SIGNATURE','SIGNATURE_REVIEW','SIGNED','ACTIVE','COMPLETED','CANCELLED'];
 for(const status of states){
  const record={status,signedScanFileId:'scan',signatureApprovedAt:'2026-11-18T08:00:00Z'};
  assert.equal(canSendContract(record),status==='DRAFT',status);
  assert.equal(canSubmitSignedScan(record),status==='SENT_FOR_SIGNATURE',status);
  assert.equal(canReviewSignedScan(record),status==='SIGNATURE_REVIEW',status);
  assert.equal(canProvisionContract(record),['SIGNED','ACTIVE','COMPLETED'].includes(status),status);
 }
 assert.equal(canReviewSignedScan({status:'SIGNATURE_REVIEW'}),false);
 assert.equal(canProvisionContract({status:'SIGNED'}),false);
 assert.equal(canProvisionContract({status:'DRAFT',signatureApprovedAt:'2026-11-18'}),false);
});

test('rejection requires a reason and does not force financial handover details',()=>{
 const approval=signingReviewFields(forms.ContractSignature,{decision:'APPROVE'}),rejection=signingReviewFields(forms.ContractSignature,{decision:'REJECT'});
 assert.equal(approval.find(field=>field.key==='accountantId').required,true);
 assert.equal(approval.find(field=>field.key==='reviewNote').required,false);
 assert.equal(rejection.find(field=>field.key==='reviewNote').required,true);
 assert.ok(!rejection.some(field=>['accountantId','handoverNote'].includes(field.key)));
 assert.ok(!forms.ContractSignature.some(field=>['signatureEvidence','signatureMethod','signedOn'].includes(field.key)));
});

test('signed scans reject empty, oversized and non-PDF files before upload',()=>{
 assert.ok(validateSignedScan(null));
 assert.ok(validateSignedScan({size:0,type:'application/pdf',name:'scan.pdf'}));
 assert.ok(validateSignedScan({size:10*1024*1024+1,type:'application/pdf',name:'scan.pdf'}));
 assert.ok(validateSignedScan({size:100,type:'image/jpeg',name:'scan.jpg'}));
 assert.equal(validateSignedScan({size:10*1024*1024,type:'application/pdf',name:'scan.pdf'}),'');
 assert.equal(validateSignedScan({size:100,type:'',name:'SCAN.PDF'}),'');
});

test('HR signing queue is separate from Manager contract editing and respects revocation',()=>{
 const code='AdmissionContractSigning';
 for(const role of ['ADMIN','LECTURER','STUDENT','ACCOUNTANT','IT_SUPPORT','MANAGER','HR']){
  assert.equal(canScreen(code,{role},[code]),role==='HR',role);
  assert.equal(screenNavigation({role},[code]).some(item=>item.code===code),role==='HR',role);
  assert.equal(canScreen(code,{role},[]),false,role);
 }
 const id='b1204aa1-2e67-4ec9-a705-6e686890a8a2';
 assert.equal(screenRoute(screenPath(code,id)).id,id);
 assert.equal(canScreen('EditTrainingContract',{role:'HR'},['EditTrainingContract']),false);
});
