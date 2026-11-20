import test from 'node:test';
import assert from 'node:assert/strict';
import {classPlanPreview,classPlanYears,contractClassChoices,weeklyPlanLabel} from '../src/screens/classPlanning.js';

test('a class plan generates matching days once within its dates even when the course crosses a year',()=>{
 const plan={planYear:2026,startsOn:'2026-12-28',endsOn:'2027-01-04',weekdays:[1,3],startTime:'08:00',endTime:'11:00'};
 assert.deepEqual(classPlanPreview(plan),{count:3,first:'2026-12-28',last:'2027-01-04'});
 assert.equal(classPlanPreview({...plan,planYear:2027}),null);
 assert.equal(classPlanPreview({...plan,endsOn:'2026-12-01'}),null);
 assert.equal(classPlanPreview({...plan,startTime:'12:00'}),null);
 assert.equal(classPlanPreview({...plan,weekdays:[]}),null);
 assert.equal(weeklyPlanLabel(plan),'Thứ Hai, Thứ Tư · 08:00 – 11:00');
 assert.deepEqual(classPlanYears([{planYear:2027},{planYear:2026},{startsOn:'2027-02-01'},{}]),[2027,2026]);
});

test('contract class choices keep only the registered course with seats and preserve the current reserved class',()=>{
 const options={admissions:[{id:'admission',enrollmentCourse:'Laptop'}],courses:[{id:'laptop',name:'Laptop'}],classes:[
  {id:'open',courseId:'laptop',status:'PLANNED',remainingSlots:5},
  {id:'full',courseId:'laptop',status:'ACTIVE',remainingSlots:0},
  {id:'other',courseId:'pc',courseName:'PC',status:'PLANNED',remainingSlots:5},
  {id:'closed',courseId:'laptop',status:'COMPLETED',remainingSlots:5}
 ]};
 assert.deepEqual(contractClassChoices({consultationId:'admission'},options).map(row=>row.id),['open']);
 assert.deepEqual(contractClassChoices({consultationId:'admission',classId:'full'},options).map(row=>row.id),['open','full']);
});
