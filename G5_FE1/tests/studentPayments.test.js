import test from 'node:test';
import assert from 'node:assert/strict';
import {hasPendingPayment,paymentAmount,paymentReviewPayload,studentPaymentPayload,validatePaymentBill,validatePaymentReview,validateStudentPayment,validPaymentDate} from '../src/screens/studentPayments.js';

const option={kind:'TUITION',remaining:14200000,transferMemo:'BKVT HD-12345678 HP'},bill={name:'bill.pdf',type:'application/pdf',size:100};
const values={kind:'TUITION',amount:'14200000',payerName:' Nguyễn Anh Đức ',paidOn:'2026-11-19',transferMemo:option.transferMemo,declaredBankReference:'claimed-reference',note:''};
test('student bill submission preserves authoritative memo and keeps the claimed bank reference separate',()=>{
 assert.deepEqual(validateStudentPayment(values,option,bill,{today:'2026-11-19'}),{});
 const payload=studentPaymentPayload({...values,bankReference:'forged-ledger-ref',status:'CONFIRMED'},option,'receipt-id');
 assert.equal(payload.transferMemo,option.transferMemo);assert.equal(payload.amount,14200000);assert.equal(payload.payerName,'Nguyễn Anh Đức');
 assert.equal(payload.declaredBankReference,'claimed-reference');assert.equal(payload.bankReference,undefined);assert.equal(payload.status,undefined);
});
test('invalid dates amounts forged memos and repeated pending bills are rejected before upload',()=>{
 const errors=validateStudentPayment({...values,amount:14200001,paidOn:'2026-11-20',transferMemo:'other memo'},option,bill,{today:'2026-11-19',submissions:[{kind:'TUITION',status:'PENDING'}]});
 for(const key of ['amount','paidOn','transferMemo','kind'])assert.ok(errors[key]);
 assert.equal(validPaymentDate('2026-02-30','2026-11-19'),false);assert.equal(validPaymentDate('2024-02-29','2026-11-19'),true);
 for(const amount of ['',0,-1,1.5,Infinity,'NaN',9007199254740992])assert.equal(paymentAmount(amount),null);
 assert.equal(hasPendingPayment([{kind:'DEPOSIT',status:'PENDING'}],'TUITION'),false);
 assert.equal(hasPendingPayment([{kind:'TUITION',status:'MISMATCH'}],'TUITION'),false);
});
test('bill validation accepts permitted evidence formats and enforces a two-megabyte limit',()=>{
 for(const type of ['application/pdf','image/png','image/jpeg'])assert.equal(validatePaymentBill({...bill,type}),'');
 assert.equal(validatePaymentBill({name:'scan.JPG',size:100,type:''}),'');
 for(const file of [null,{...bill,size:0},{...bill,size:2097153},{...bill,type:'text/html'}])assert.ok(validatePaymentBill(file));
});
test('accountant confirmation requires the actual statement identity amount memo date and all checkboxes',()=>{
 const review={bankReference:' BANK-ACTUAL ',statementAmount:'14200000',statementMemo:' bkvt   hd-12345678 hp ',statementDate:'2026-11-19',identityVerified:true,amountVerified:true,referenceVerified:true,changeNote:' Khớp sao kê '};
 assert.deepEqual(validatePaymentReview(review,{amount:14200000},option.transferMemo,'2026-11-19'),{});
 const payload=paymentReviewPayload({...review,status:'CONFIRMED',transactionId:'forged'},3);
 assert.equal(payload.bankReference,'BANK-ACTUAL');assert.equal(payload.version,3);assert.equal(payload.statementAmount,14200000);assert.equal(payload.status,undefined);assert.equal(payload.transactionId,undefined);
 const errors=validatePaymentReview({...review,bankReference:'',statementAmount:'100',statementMemo:'other',referenceVerified:false},{amount:14200000},option.transferMemo,'2026-11-19');
 for(const key of ['bankReference','statementAmount','statementMemo','verified'])assert.ok(errors[key]);
});
