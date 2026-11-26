import test from 'node:test';
import assert from 'node:assert/strict';
import {forms} from '../src/screens/definitions.js';
import {canRecordConsultation,consultationFields,consultationPayload,newConsultationValues} from '../src/screens/admissionConsultation.js';

const fields=status=>Object.fromEntries(consultationFields(forms.AdmissionConsultation,{status}).map(field=>[field.key,field]));

test('each consultation prefills the current email and starts with fresh consultation fields',()=>{
 for(const status of ['NEW','NEEDS_COUNSELING','DOCUMENT_APPOINTMENT']){
  const record={id:'admission-id',version:8,status,email:'learner@gmail.com',channel:'PHONE',notes:'Previous notes',appointmentAt:'2026-12-15T01:30:00Z',documentDueOn:'2026-12-20',discontinuationReason:'Previous reason'};
  const first=newConsultationValues(record);
  assert.equal(first.id,record.id);assert.equal(first.version,8);
  assert.equal(first.email,record.email);
  for(const key of ['channel','status','notes','appointmentAt','documentDueOn','discontinuationReason'])assert.equal(first[key],'',key);
  first.notes='Unsaved draft';
  assert.equal(newConsultationValues(record).notes,'');assert.equal(record.notes,'Previous notes');
 }
});

test('the email input is editable and only a new address is submitted',()=>{
 const currentEmail='learner@gmail.com';
 const current=newConsultationValues({email:currentEmail});
 assert.equal(fields('NEW').email.type,'email');
 assert.notEqual(fields('NEW').email.readOnly,true);
 assert.equal(newConsultationValues({email:null}).email,'');
 assert.equal(consultationPayload(current,currentEmail).email,undefined);
 assert.equal(consultationPayload({...current,email:''},currentEmail).email,undefined);
 assert.equal(consultationPayload({...current,email:'updated@gmail.com'},currentEmail).email,'updated@gmail.com');
});

test('legacy managed states retain their automatic stage defensively while consultation access is blocked',()=>{
 for(const status of ['PROFILE_PENDING','CONTRACTED','PROFILE_CREATED']){
  const values=newConsultationValues({id:'admission-id',version:4,status,email:'learner@gmail.com',channel:'EMAIL',notes:'Previous notes',documentDueOn:'2026-12-20'});
  assert.equal(values.status,status);assert.equal(values.version,4);
  assert.equal(values.email,'learner@gmail.com');
  for(const key of ['channel','notes','appointmentAt','documentDueOn','discontinuationReason'])assert.equal(values[key],'');
  assert.equal(fields(status).status.disabled,true);
  assert.equal(canRecordConsultation(status),false);
 }
});

test('discontinued and submitted admissions cannot be used for another consultation',()=>{
 for(const status of ['NOT_PROCEEDING','PROFILE_PENDING','CONTRACTED','PROFILE_CREATED'])assert.equal(canRecordConsultation(status),false);
 for(const status of ['NEW','CONTACTED','NEEDS_COUNSELING','DOCUMENT_APPOINTMENT'])assert.equal(canRecordConsultation(status),true);
});

test('further counselling asks for content and an optional callback without submission or refusal fields',()=>{
 const visible=fields('NEEDS_COUNSELING');
 assert.equal(visible.notes.required,true);assert.equal(visible.appointmentAt.required,undefined);
 assert.equal(visible.documentDueOn,undefined);assert.equal(visible.discontinuationReason,undefined);
 assert.deepEqual(visible.status.source,['NEEDS_COUNSELING','DOCUMENT_APPOINTMENT','NOT_PROCEEDING']);
 const value=consultationPayload({status:'NEEDS_COUNSELING',notes:'Cần giải đáp học phí',appointmentAt:'2026-12-15T09:00',documentDueOn:'2026-12-20',discontinuationReason:'Old reason'});
 assert.equal(value.appointmentAt,'2026-12-15T09:00');assert.equal(value.documentDueOn,'');assert.equal(value.discontinuationReason,'');
});

test('document submission asks for a required date and permits saving without consultation content',()=>{
 const visible=fields('DOCUMENT_APPOINTMENT');
 assert.equal(visible.documentDueOn.type,'date');assert.equal(visible.documentDueOn.required,true);assert.equal(visible.notes.required,false);
 assert.equal(visible.appointmentAt,undefined);assert.equal(visible.discontinuationReason,undefined);
 const value=consultationPayload({status:'DOCUMENT_APPOINTMENT',documentDueOn:'2026-12-20',appointmentAt:'2026-12-15T09:00',discontinuationReason:'Old reason'});
 assert.equal(value.documentDueOn,'2026-12-20');assert.equal(value.notes,'');assert.equal(value.appointmentAt,'');assert.equal(value.discontinuationReason,'');
});

test('discontinuation asks for a separate reason and removes earlier appointments and counselling content from submission',()=>{
 const visible=fields('NOT_PROCEEDING');assert.equal(visible.discontinuationReason.required,true);
 assert.equal(visible.notes,undefined);assert.equal(visible.appointmentAt,undefined);assert.equal(visible.documentDueOn,undefined);
 const value=consultationPayload({status:'NOT_PROCEEDING',discontinuationReason:'Không còn nhu cầu',notes:'Old notes',documentDueOn:'2026-12-20',appointmentAt:'2026-12-15T09:00'});
 assert.equal(value.discontinuationReason,'Không còn nhu cầu');assert.equal(value.notes,'');assert.equal(value.appointmentAt,'');assert.equal(value.documentDueOn,'');
});

test('legacy automatic form values cannot change stage and do not grant consultation access',()=>{
 for(const status of ['PROFILE_PENDING','CONTRACTED','PROFILE_CREATED']){
  const visible=fields(status);assert.equal(visible.status.disabled,true);assert.deepEqual(visible.status.source,[status]);assert.equal(visible.notes.required,true);
  assert.equal(canRecordConsultation(status),false);
  assert.equal(visible.documentDueOn,undefined);assert.equal(visible.discontinuationReason,undefined);
  const value={status,notes:'Bổ sung nội dung tư vấn',documentDueOn:'2026-12-20'};assert.deepEqual(consultationPayload(value),value);
 }
});
