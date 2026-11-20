import test from 'node:test';
import assert from 'node:assert/strict';
import {screens} from '../src/screens/access.js';
import {rolesById,dashboardPath} from '../src/roles.js';
import {breadcrumbHome,breadcrumbLocation,initialBreadcrumbTrail,restoreBreadcrumbTrail,updateBreadcrumbTrail} from '../src/workspaceBreadcrumbs.js';

const record='b1204aa1-2e67-4ec9-a705-6e686890a8a2',other='b1204aa1-2e67-4ec9-a705-6e686890a8a3';
const actor=role=>({id:42,roleId:Number(Object.keys(rolesById).find(id=>rolesById[id]===role)),role});
const grants=role=>[...new Set(Object.values(screens).filter(screen=>!screen.public&&screen.roles.includes(role)).map(screen=>screen.functionCode))];
const paths=trail=>trail.map(item=>item.href);
const manager=actor('MANAGER'),hr=actor('HR'),admin=actor('ADMIN');

test('all seven roles get their own permitted dashboard and notification ancestors',()=>{
 for(const role of Object.values(rolesById)){
  const user=actor(role),functions=grants(role),target='/screens/ViewNotification/'+record;
  assert.equal(breadcrumbHome(user,functions),dashboardPath(user));
  assert.deepEqual(paths(initialBreadcrumbTrail(target,user,functions)),[dashboardPath(user),'/screens/ListNotification',target],role);
 }
});

test('admin account list, detail and edit have precise clickable parents and retain list queries',()=>{
 const functions=grants('ADMIN'),list='/admin/users?status=ACTIVE#results',detail='/admin/users/51/view',edit='/admin/users/51/edit';
 let trail=initialBreadcrumbTrail('/dashboard/admin',admin,functions);
 for(const path of [list,detail,edit])trail=updateBreadcrumbTrail(trail,path,admin,functions);
 assert.deepEqual(paths(trail),['/dashboard/admin',list,detail,edit]);
 assert.deepEqual(trail.map(item=>item.label),['Dashboard','Tài khoản','Chi tiết tài khoản','Chỉnh sửa tài khoản']);
 assert.deepEqual(paths(updateBreadcrumbTrail(trail,list,admin,functions,{trailIndex:1})),['/dashboard/admin',list]);
 assert.deepEqual(paths(initialBreadcrumbTrail(edit,admin,functions)),['/dashboard/admin','/admin/users',detail,edit]);
});

test('manager retains the actual account-to-contract workflow rather than an unrelated hardcoded chain',()=>{
 const functions=grants('MANAGER'),list='/screens/ListStudentAccount?q=learner',create='/screens/AddTrainingContract?consultationId='+record,detail='/screens/TrainingContractDetail/'+other,review='/screens/ContractSignature/'+other;
 let trail=initialBreadcrumbTrail(list,manager,functions);
 for(const path of [create,detail,review])trail=updateBreadcrumbTrail(trail,path,manager,functions);
 assert.deepEqual(paths(trail),['/dashboard/manager',list,create,detail,review]);
 assert.deepEqual(paths(updateBreadcrumbTrail(trail,detail,manager,functions,{trailIndex:3})),['/dashboard/manager',list,create,detail]);
 assert.deepEqual(paths(initialBreadcrumbTrail(review,manager,functions)),['/dashboard/manager','/screens/ListTrainingContract',detail,review]);
});

test('query-only admission tab changes replace one step and detail links retain the correct enrollment tab',()=>{
 const functions=grants('HR'),waiting='/screens/ListAdmissionEnrollment?tab=waiting',forwarded='/screens/ListAdmissionEnrollment?tab=forwarded',detail='/screens/ListAdmissionApplicationDetail/'+record+'?from=ListAdmissionEnrollment&tab=forwarded';
 let trail=initialBreadcrumbTrail(waiting,hr,functions);
 trail=updateBreadcrumbTrail(trail,forwarded,hr,functions);
 assert.deepEqual(paths(trail),['/dashboard/hr',forwarded]);
 trail=updateBreadcrumbTrail(trail,detail,hr,functions);
 assert.deepEqual(paths(trail),['/dashboard/hr',forwarded,detail]);
 assert.deepEqual(paths(initialBreadcrumbTrail(detail,hr,functions)),paths(trail));
 assert.deepEqual(paths(updateBreadcrumbTrail(trail,forwarded,hr,functions)),['/dashboard/hr',forwarded]);
});

test('top-level sidebar navigation starts a new branch instead of growing an unrelated visit log',()=>{
 const functions=grants('HR'),detail='/screens/ListAdmissionApplicationDetail/'+record;
 const previous=initialBreadcrumbTrail(detail,hr,functions);
 assert.deepEqual(paths(updateBreadcrumbTrail(previous,'/screens/ListNotification',hr,functions)),['/dashboard/hr','/screens/ListNotification']);
 assert.deepEqual(paths(updateBreadcrumbTrail(previous,'/profile',hr,functions)),['/dashboard/hr','/profile']);
});

test('restoring browser history or reload recovers exact URLs while recalculating untrusted labels',()=>{
 const functions=grants('ADMIN'),list='/admin/users?roleId=3#accounts',edit='/admin/users/51/edit';
 let trail=initialBreadcrumbTrail(list,admin,functions);
 trail=updateBreadcrumbTrail(trail,'/admin/users/51/view',admin,functions);
 trail=updateBreadcrumbTrail(trail,edit,admin,functions);
 const serialized=JSON.parse(JSON.stringify(trail)).map(item=>({...item,label:'Client forged label'}));
 const restored=restoreBreadcrumbTrail(serialized,edit,admin,functions);
 assert.deepEqual(paths(restored),paths(trail));assert.equal(restored.at(-1).label,'Chỉnh sửa tài khoản');
 assert.equal(restored.some(item=>item.label==='Client forged label'),false);
 assert.deepEqual(paths(restoreBreadcrumbTrail(serialized,list,admin,functions)),['/dashboard/admin',list]);
});

test('permission revocation removes unavailable ancestors and cannot be bypassed by serialized history',()=>{
 const functions=grants('MANAGER'),detail='/screens/TrainingContractDetail/'+record;
 const prior=initialBreadcrumbTrail(detail,manager,functions);
 const reduced=functions.filter(code=>code!=='ListTrainingContract');
 assert.deepEqual(paths(restoreBreadcrumbTrail(prior,detail,manager,reduced)),['/dashboard/manager',detail]);
 const revoked=reduced.filter(code=>code!=='TrainingContractDetail');
 const denied=restoreBreadcrumbTrail(prior,detail,manager,revoked);
 assert.deepEqual(paths(denied),['/dashboard/manager',detail]);assert.equal(denied.at(-1).label,'Không có quyền truy cập');
});

test('stale grants never expose another role dashboard or Admin navigation in breadcrumbs',()=>{
 const student=actor('STUDENT'),functions=[...grants('STUDENT'),'TrainingContractDetail','USER_ACCOUNTS'];
 for(const target of ['/dashboard/manager','/admin/users','/screens/TrainingContractDetail/'+record]){
  const trail=initialBreadcrumbTrail(target,student,functions);
  assert.equal(trail[0].href,'/dashboard/student');assert.equal(trail.at(-1).label,'Không có quyền truy cập');
  assert.equal(trail.slice(0,-1).some(item=>item.href.startsWith('/admin/')||item.href==='/dashboard/manager'),false);
 }
 assert.equal(breadcrumbHome({roleId:7,role:'MANAGER'},['DASHBOARD']),null);
});

test('workspace locations preserve queries and fragments while rejecting unsafe or external targets',()=>{
 assert.equal(breadcrumbLocation('/screens/ListNotification/?q=hello%20world#result'),'/screens/ListNotification?q=hello%20world#result');
 for(const target of ['https://evil.test/','//evil.test/path','javascript:alert(1)','/\\evil.test','/profile\n','/bad path',null,{}])assert.equal(breadcrumbLocation(target),null);
 const functions=grants('HR'),trail=initialBreadcrumbTrail('/screens/ListNotification',hr,functions);
 assert.deepEqual(updateBreadcrumbTrail(trail,'//evil.test/path',hr,functions),trail);
 const restored=restoreBreadcrumbTrail([{href:'https://evil.test',label:'Fake'},...trail],'/screens/ListNotification',hr,functions);
 assert.deepEqual(restored,trail);
});

test('leaving a forbidden page cannot turn its current-only label into a clickable ancestor',()=>{
 const student=actor('STUDENT'),functions=grants('STUDENT'),forbidden='/screens/TrainingContractDetail/'+record;
 const denied=initialBreadcrumbTrail(forbidden,student,functions);
 const next=updateBreadcrumbTrail(denied,'/screens/ViewNotification/'+other,student,functions);
 assert.deepEqual(paths(next),['/dashboard/student','/screens/ListNotification','/screens/ViewNotification/'+other]);
 assert.equal(next.some(item=>item.href===forbidden),false);
});

test('direct links never invent another entity ID for student contracts or equipment receipts',()=>{
 const student=actor('STUDENT'),studentTrail=initialBreadcrumbTrail('/screens/TuitionDetail_Student/'+record,student,grants('STUDENT'));
 assert.deepEqual(paths(studentTrail),['/dashboard/student','/screens/StudentProfileDetail_Student','/screens/TuitionDetail_Student/'+record]);
 assert.equal(studentTrail.some(item=>item.href==='/screens/StudentProfileDetail_Student/'+record),false);
 const support=actor('IT_SUPPORT'),receipt=initialBreadcrumbTrail('/screens/AssetHandover_Receipt/'+record,support,grants('IT_SUPPORT'));
 assert.deepEqual(paths(receipt),['/dashboard/it-support','/screens/ListEquipmentRequest_IT','/screens/AssetHandover_Receipt/'+record]);
 assert.equal(receipt.some(item=>item.href==='/screens/AssetHandover/'+record),false);
});

test('class timetable editing returns to its actual class when a valid context is present',()=>{
 const functions=grants('ADMIN'),target='/screens/TimeTable_EditSession/'+record+'?returnClassId='+other;
 assert.deepEqual(paths(initialBreadcrumbTrail(target,admin,functions)),['/dashboard/admin','/screens/CourseSchedules','/screens/ClassDetail/'+other,target]);
 const invalid='/screens/TimeTable_EditSession/'+record+'?returnClassId=not-a-valid-id';
 assert.deepEqual(paths(initialBreadcrumbTrail(invalid,admin,functions)),['/dashboard/admin','/screens/TimeTable_Manager',invalid]);
});

test('record siblings replace the current record, while returning from edit truncates to its parent',()=>{
 const functions=grants('MANAGER'),first='/screens/ClassDetail/'+record,second='/screens/ClassDetail/'+other;
 let trail=initialBreadcrumbTrail(first,manager,functions);
 trail=updateBreadcrumbTrail(trail,second,manager,functions);
 assert.deepEqual(paths(trail),['/dashboard/manager','/screens/ListClass',second]);
 trail=updateBreadcrumbTrail(trail,'/screens/EditClass/'+other,manager,functions);
 assert.deepEqual(paths(updateBreadcrumbTrail(trail,second,manager,functions,{replace:true})),['/dashboard/manager','/screens/ListClass',second]);
});

test('accounts without Dashboard use the first accessible home and master-data parents remain role scoped',()=>{
 const limited=['ListNotification','ViewNotification'];
 assert.equal(breadcrumbHome(hr,limited),'/screens/ListNotification');
 assert.deepEqual(paths(initialBreadcrumbTrail('/screens/ViewNotification/'+record,hr,limited)),['/screens/ListNotification','/screens/ViewNotification/'+record]);
 assert.deepEqual(paths(initialBreadcrumbTrail('/screens/EditClassroom/'+record,admin,grants('ADMIN'))),['/dashboard/admin','/screens/ListClassroom','/screens/EditClassroom/'+record]);
});

test('the signing queue detail has a distinct current title and a clickable queue parent',()=>{
 const functions=grants('HR'),detail='/screens/AdmissionContractSigning/'+record,trail=initialBreadcrumbTrail(detail,hr,functions);
 assert.deepEqual(paths(trail),['/dashboard/hr','/screens/AdmissionContractSigning',detail]);
 assert.equal(trail.at(-2).label,'In & ký hợp đồng');assert.equal(trail.at(-1).label,'Chi tiết hợp đồng cần ký');
});
