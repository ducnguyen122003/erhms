import test from 'node:test';
import assert from 'node:assert/strict';
import {canScreen,screenNavigation,screens} from '../src/screens/access.js';
import {initialBreadcrumbTrail} from '../src/workspaceBreadcrumbs.js';

const admin={role:'ADMIN',roleId:1,id:1};
const grants=[...new Set(Object.values(screens).filter(screen=>screen.roles.includes('ADMIN')).map(screen=>screen.functionCode))];

test('Admin course-first schedule entry replaces the three former catalog and class menus',()=>{
 const navigation=screenNavigation(admin,grants);
 assert.equal(navigation.filter(item=>item.code==='CourseSchedules').length,1);
 for(const code of ['ListProfession','ListCourse','ListClass']){
  assert.equal(navigation.some(item=>item.code===code),false,code);
  assert.equal(canScreen(code,admin,[code]),false,code);
 }
 for(const code of ['AddCourse','EditCourse','AddProfession','EditProfession'])assert.equal(canScreen(code,admin,[code]),false,code);
 assert.equal(canScreen('ListClass',{role:'MANAGER'},['ListClass']),true);
});

test('Admin class plans have the course schedules entry as their clickable parent',()=>{
 const id='20000000-0000-4000-8000-000000000001';
 for(const code of ['AddClass','ClassDetail']){
  const target='/screens/'+code+(code==='AddClass'?'':'/'+id);
  assert.deepEqual(initialBreadcrumbTrail(target,admin,['DASHBOARD',...grants]).map(item=>item.href),['/dashboard/admin','/screens/CourseSchedules',target]);
 }
});
