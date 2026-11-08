import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {canScreen,dashboardLink,screenNavigation,screenPath,screenRoute,screens} from '../src/screens/access.js';
import {workspaceRoute} from '../src/roles.js';

const roles=['ADMIN','LECTURER','STUDENT','ACCOUNTANT','IT_SUPPORT','MANAGER','HR'];
const grants=role=>[...new Set(Object.values(screens).filter(s=>!s.public&&s.roles.includes(role)).map(s=>s.functionCode))];
test('all 108 workbook screens and 12 master data screens are mapped to separate new components or an existing implementation',()=>{
 assert.equal(Object.keys(screens).length,120);
 let business=0,pages=0;
 for(const screen of Object.values(screens)){
  if(screen.code===screen.functionCode&&!screen.public){assert.ok(fs.existsSync(new URL('../src/screens/pages/'+screen.code+'.jsx',import.meta.url)));business++;}
  else if(screen.public&&screen.fe==='FE-03'){assert.ok(fs.existsSync(new URL('../src/screens/public/'+screen.code+'.jsx',import.meta.url)));pages++;}
  else assert.ok(screenPath(screen.code).startsWith('/'));
 }
 assert.equal(business,92);assert.equal(pages,9);
});
test('confirmed screen access matches the seven role totals in the XLSX including public and shared screens',()=>{
 const totals=[28,30,26,41,44,53,31];
 roles.forEach((role,index)=>{const user={role,roleId:index+1};assert.equal(Object.keys(screens).filter(code=>canScreen(code,user,grants(role))).length,totals[index],role);});
});
test('proposed rights and grants outside the declared role do not enable business screens',()=>{
 for(const role of ['ADMIN','LECTURER','HR'])assert.equal(canScreen('SendNotification',{role},['SendNotification']),false);
 assert.equal(canScreen('AssetDetail',{role:'LECTURER'},['AssetDetail']),false);
 assert.equal(canScreen('HistoryAssetAssignment',{role:'ACCOUNTANT'},['HistoryAssetAssignment']),false);
 assert.equal(canScreen('AssetIncidentDetail',{role:'MANAGER'},['AssetIncidentDetail']),false);
});
test('business routes validate identifiers and apply revocation and role scoping',()=>{
 const manager={roleId:6,role:'MANAGER'},student={roleId:3,role:'STUDENT'};
 for(const id of ['42','b1204aa1-2e67-4ec9-a705-6e686890a8a2'])assert.equal(screenRoute('/screens/ClassDetail/'+id).id,id);
 for(const path of ['/screens/Unknown','/screens/ClassDetail/../admin','/screens/ClassDetail/0','/screens/ClassDetail/bad-id','/screens/ClassDetail/42/extra'])assert.equal(screenRoute(path),null);
 assert.equal(workspaceRoute('/screens/ClassDetail/42',manager,['ClassDetail']),'screen');
 assert.equal(workspaceRoute('/screens/ClassDetail/42',manager,[]),'forbidden');
 assert.equal(workspaceRoute('/screens/ClassDetail/42',student,['ClassDetail']),'forbidden');
 assert.equal(workspaceRoute('/screens/ClassDetail/42',{roleId:8,role:'MANAGER'},['ClassDetail']),'unsupported');
});
test('menu links only include granted role screens and never expose a student request or withdrawal action',()=>{
 roles.forEach((role,index)=>{const user={role,roleId:index+1};for(const item of screenNavigation(user,grants(role)))assert.equal(canScreen(item.code,user,grants(role)),true);});
 const student={roleId:3,role:'STUDENT'},codes=screenNavigation(student,grants('STUDENT')).map(item=>item.code);
 assert.ok(codes.includes('StudentAssets'));assert.ok(codes.includes('StudentResults'));assert.ok(!codes.includes('AddEquipmentRequest'));assert.ok(!codes.includes('RecordWithdrawal'));
 assert.deepEqual(screenNavigation(student,[]),[]);
});
test('legacy account and dashboard paths keep their existing contracts',()=>{
 assert.equal(screenPath('EditUser',42),'/admin/users/42/edit');assert.equal(screenPath('EditUser'),'/admin/users/new');assert.equal(screenPath('UserProfile_AdminView',42),'/admin/users/42/view');
 assert.equal(screenPath('Dashboard_Admissions'),'/dashboard/hr');assert.equal(screenPath('Contact'),'/public/Contact');
});
test('dashboard records and tasks lead to the appropriate role screen and respect revocation',()=>{
 const id='b1204aa1-2e67-4ec9-a705-6e686890a8a2';
 assert.equal(dashboardLink({role:'LECTURER'},{panelKey:'classes',row:{id}},['ClassDetail_Lecturer']),'/screens/ClassDetail_Lecturer/'+id);
 assert.equal(dashboardLink({role:'ACCOUNTANT'},{panelKey:'transactions',row:{id}},['PaymentDetail']),'/screens/PaymentDetail/'+id);
 assert.equal(dashboardLink({role:'STUDENT'},{panelKey:'equipment',row:{id}},['StudentAssetReceipt']),'/screens/StudentAssetReceipt/'+id);
 assert.equal(dashboardLink({role:'HR'},{taskKey:'drafts'},['ListTrainingContract']),'/screens/ListTrainingContract');
 assert.equal(dashboardLink({role:'MANAGER'},{panelKey:'assignments',row:{id}},[]),null);
 assert.equal(screenPath('ChangePassword'),'/screens/ChangePassword');
});

test('master data screens have distinct components and strict role permissions',()=>{
 const groups=[['EquipmentGroup','IT_SUPPORT'],['StorageLocation','IT_SUPPORT'],['Profession','MANAGER'],['Classroom','ADMIN']];
 for(const [suffix,role] of groups){
  for(const mode of ['List','Add','Edit']){
   const code=mode+suffix;assert.equal(screens[code].functionCode,code);
   assert.equal(canScreen(code,{role},[code]),true);assert.equal(canScreen(code,{role},[]),false);
   for(const other of roles.filter(r=>r!==role))assert.equal(canScreen(code,{role:other},[code]),false);
  }
  assert.ok(screenNavigation({role},grants(role)).some(item=>item.code==='List'+suffix));
 }
});
