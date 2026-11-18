import test from 'node:test';
import assert from 'node:assert/strict';
import {admissionDashboardCodes,admissionEnrollmentTab,admissionHome,admissionMenuActive} from '../src/screens/admissionDashboard.js';
import {canScreen,screenNavigation} from '../src/screens/access.js';

test('the HR menu has two independently granted admission dashboards',()=>{
 const user={role:'HR',roleId:7};
 const navigation=screenNavigation(user,admissionDashboardCodes);
 assert.deepEqual(navigation.map(item=>item.label),['Tư vấn & tiếp nhận hồ sơ','Hoàn tất thủ tục nhập học']);
 assert.deepEqual(screenNavigation(user,['ListAdmissionApplication']).map(item=>item.code),['ListAdmissionApplication']);
 assert.deepEqual(screenNavigation(user,['ListAdmissionEnrollment']).map(item=>item.code),['ListAdmissionEnrollment']);
 for(const role of ['ADMIN','MANAGER','STUDENT','ACCOUNTANT','LECTURER','IT_SUPPORT'])assert.equal(canScreen('ListAdmissionEnrollment',{role},['ListAdmissionEnrollment']),false);
});
test('detail navigation uses the current completeness after additions or removals',()=>{
 assert.equal(admissionHome({status:'PROFILE_PENDING',documentStatus:'DOCUMENTS_COMPLETE'}),'ListAdmissionEnrollment');
 assert.equal(admissionHome({status:'PROFILE_CREATED',documentStatus:'DOCUMENTS_INCOMPLETE'}),'ListAdmissionApplication');
 assert.equal(admissionHome({status:'NEW'}),'ListAdmissionApplication');
 assert.equal(admissionHome({forwardedAt:'2026-11-18T01:00:00Z',documentStatus:'DOCUMENTS_INCOMPLETE'}),'ListAdmissionEnrollment');
});
test('enrollment pages default to the waiting list and use explicit forwarded URLs',()=>{
 assert.equal(admissionEnrollmentTab(), 'waiting');
 assert.equal(admissionEnrollmentTab('?tab=forwarded'),'forwarded');
 assert.equal(admissionEnrollmentTab('?tab=waiting'),'waiting');
 assert.equal(admissionEnrollmentTab('?tab=untrusted'),'waiting');
});
test('only one dashboard menu entry is active and detail pages keep their origin',()=>{
 assert.equal(admissionMenuActive('ListAdmissionApplication','ListAdmissionApplication'),true);
 assert.equal(admissionMenuActive('ListAdmissionApplication','ListAdmissionEnrollment'),false);
 assert.equal(admissionMenuActive('ListAdmissionEnrollment','ListAdmissionApplication'),false);
 assert.equal(admissionMenuActive('ListAdmissionEnrollment','ListAdmissionEnrollment'),true);
 assert.equal(admissionMenuActive('ListAdmissionApplicationDetail','ListAdmissionEnrollment','?from=ListAdmissionEnrollment'),true);
 assert.equal(admissionMenuActive('ListAdmissionApplicationDetail','ListAdmissionApplication','?from=ListAdmissionEnrollment'),false);
 assert.equal(admissionMenuActive('AdmissionConsultation','ListAdmissionApplication','?from=untrusted'),true);
});
