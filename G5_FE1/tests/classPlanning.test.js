import test from 'node:test';
import assert from 'node:assert/strict';
import {changeClassPlanValue,classPlanDefaultEnd,classPlanPreview,classPlanYears,contractClassChoices,weeklyPlanLabel} from '../src/screens/classPlanning.js';

test('planned course end dates use calendar months with month-end clamping and leap years',()=>{
 assert.equal(classPlanDefaultEnd('2026-11-23',4),'2027-03-23');
 assert.equal(classPlanDefaultEnd('2026-11-23',7),'2027-06-23');
 assert.equal(classPlanDefaultEnd('2027-01-31',1),'2027-02-28');
 assert.equal(classPlanDefaultEnd('2028-01-31',1),'2028-02-29');
 assert.equal(classPlanDefaultEnd('2026-02-30',4),'');
 assert.equal(classPlanDefaultEnd('',4),'');
 assert.equal(classPlanDefaultEnd('2026-11-23',undefined),'');
});

test('course or start changes refresh automatic dates and preserve a valid manual calendar override',()=>{
 const options={courses:[{id:'laptop',name:'Laptop',durationMonths:4,classCapacity:20},{id:'inverter',name:'Inverter',durationMonths:7,classCapacity:25}]};
 const blank={courseId:'laptop',startsOn:'',endsOn:'',name:'Laptop'};
 const planned=changeClassPlanValue(blank,'startsOn','2026-11-23',options);
 assert.equal(planned.endsOn,'2027-03-23');
 assert.equal(changeClassPlanValue(planned,'startsOn','2026-11-24',options).endsOn,'2027-03-24');
 assert.equal(changeClassPlanValue(planned,'courseId','inverter',options).endsOn,'2027-06-23');
 assert.equal(changeClassPlanValue({...planned,endsOn:'2027-04-01'},'startsOn','2026-12-01',options).endsOn,'2027-04-01');
 assert.equal(changeClassPlanValue({...planned,endsOn:'2027-04-01'},'courseId','inverter',options).endsOn,'2027-04-01');
 assert.equal(changeClassPlanValue({...planned,endsOn:'2026-11-23'},'startsOn','2026-12-01',options).endsOn,'2027-04-01');
 assert.equal(changeClassPlanValue({...blank,startsOn:'2026-11-23',courseId:''},'courseId','laptop',options).endsOn,'2027-03-23');
});

test('the preview identifies selected weekdays absent from a one-day class interval',()=>{
 const oneDay={planYear:2026,startsOn:'2026-11-23',endsOn:'2026-11-23',weekdays:[1,2,3,4,5],startTime:'08:00',endTime:'11:00'};
 assert.deepEqual(classPlanPreview(oneDay),{count:1,first:'2026-11-23',last:'2026-11-23',missingWeekdays:[2,3,4,5]});
 assert.deepEqual(classPlanPreview({...oneDay,endsOn:classPlanDefaultEnd(oneDay.startsOn,4)}),{count:87,first:'2026-11-23',last:'2027-03-23',missingWeekdays:[]});
});

test('a class plan generates matching days once within its dates even when the course crosses a year',()=>{
 const plan={planYear:2026,startsOn:'2026-12-28',endsOn:'2027-01-04',weekdays:[1,3],startTime:'08:00',endTime:'11:00'};
 assert.deepEqual(classPlanPreview(plan),{count:3,first:'2026-12-28',last:'2027-01-04',missingWeekdays:[]});
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
  {id:'duplicate-name',courseId:'other-laptop',courseName:'Laptop',status:'PLANNED',remainingSlots:5},
  {id:'other',courseId:'pc',courseName:'PC',status:'PLANNED',remainingSlots:5},
  {id:'closed',courseId:'laptop',status:'COMPLETED',remainingSlots:5}
 ]};
 assert.deepEqual(contractClassChoices({consultationId:'admission'},options),[]);
 assert.deepEqual(contractClassChoices({consultationId:'admission',courseId:'laptop'},options).map(row=>row.id),['open']);
 assert.deepEqual(contractClassChoices({consultationId:'admission',courseId:'laptop',classId:'full'},options).map(row=>row.id),['open','full']);
});
