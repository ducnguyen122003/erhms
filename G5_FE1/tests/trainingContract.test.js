import test from 'node:test';
import assert from 'node:assert/strict';
import {changeTrainingContractValue,initialTrainingContractValues,trainingContractCourseField,trainingContractSelection} from '../src/screens/trainingContract.js';

const catalog=[
 {name:'Sửa chữa máy tính Laptop chuyên nghiệp',durationMonths:4,tuitionFee:14200000},
 {name:'Sửa chữa máy tính PC & máy in',durationMonths:4,tuitionFee:13000000},
 {name:'Sửa chữa Điện tử - Điện gia dụng',durationMonths:4,tuitionFee:11000000},
 {name:'Sửa chữa Điện lạnh, Điện gia dụng & Inverter',durationMonths:7,tuitionFee:21900000}
];
const fixtures=()=>({
 enrollmentCourseCatalog:catalog,
 admissions:catalog.map((entry,index)=>({id:'admission-'+index,enrollmentCourse:entry.name,email:' Learner'+index+'@GMAIL.COM '})),
 courses:catalog.map((entry,index)=>({...entry,id:'course-'+index,status:'OPEN',tuitionFee:1}))
});
const courseField={key:'courseId',label:'Khóa học',type:'select',source:'courses',required:true};

test('choosing a planned class fixes the start date and course while admission changes clear the old class',()=>{
 const options=fixtures();options.classes=[{id:'class-0',courseId:'course-0',courseName:catalog[0].name,status:'PLANNED',remainingSlots:3,planYear:2027,startsOn:'2027-01-04',endsOn:'2027-04-30'}];
 const initial=initialTrainingContractValues('AddTrainingContract',{consultationId:'admission-0',classId:'old',startsOn:'2026-01-01'},options);
 assert.equal(initial.classId,'');assert.equal(initial.startsOn,'');
 const selected=changeTrainingContractValue(initial,'classId','class-0',options);
 assert.equal(selected.classId,'class-0');assert.equal(selected.courseId,'course-0');assert.equal(selected.startsOn,'2027-01-04');
 assert.equal(trainingContractSelection(selected,options).selectedClass.id,'class-0');
 const negotiated=changeTrainingContractValue(selected,'tuitionDue',12000000,options);
 assert.equal(negotiated.tuitionDue,12000000);
 const changed=changeTrainingContractValue(negotiated,'consultationId','admission-1',options);
 assert.equal(changed.classId,'');assert.equal(changed.startsOn,'');assert.equal(trainingContractSelection(changed,options).classes.length,0);
});

test('legacy admissions choose a course before selecting a class, which sets only the start date',()=>{
 const options=fixtures();options.admissions.push({id:'legacy',email:'legacy@gmail.com'});
 options.classes=[
  {id:'laptop-2027',courseId:'course-0',courseName:catalog[0].name,status:'PLANNED',remainingSlots:2,planYear:2027,startsOn:'2027-02-01'},
  {id:'pc-2027',courseId:'course-1',courseName:catalog[1].name,status:'PLANNED',remainingSlots:2,planYear:2027,startsOn:'2027-03-01'}
 ];
 const initial=initialTrainingContractValues('AddTrainingContract',{consultationId:'legacy'},options);
 assert.deepEqual(trainingContractCourseField(courseField,trainingContractSelection(initial,options)),courseField);
 assert.deepEqual(trainingContractSelection(initial,options).classes,[]);
 const course=changeTrainingContractValue(initial,'courseId','course-1',options);
 assert.deepEqual(trainingContractSelection(course,options).classes.map(row=>row.id),['pc-2027']);
 const wrong=changeTrainingContractValue(course,'classId','laptop-2027',options);
 assert.equal(wrong.classId,'');assert.equal(wrong.courseId,'course-1');assert.equal(wrong.startsOn,'');
 const selected=changeTrainingContractValue(course,'classId','pc-2027',options);
 assert.equal(selected.courseId,'course-1');assert.equal(selected.startsOn,'2027-03-01');
 const switched=changeTrainingContractValue(selected,'courseId','course-0',options);
 assert.equal(switched.classId,'');assert.equal(switched.startsOn,'');
 assert.deepEqual(trainingContractSelection(switched,options).classes.map(row=>row.id),['laptop-2027']);
});

test('reopening a draft keeps its course ID when its saved class belongs to another course',()=>{
 const options=fixtures();options.classes=[{id:'wrong-class',courseId:'course-1',courseName:catalog[0].name,status:'PLANNED',startsOn:'2027-03-01'}];
 const draft={id:'draft',consultationId:'admission-0',courseId:'course-0',classId:'wrong-class',startsOn:'2027-03-01',tuitionDue:12000000};
 const opened=initialTrainingContractValues('EditTrainingContract',draft,options);
 assert.equal(opened.courseId,'course-0');assert.equal(opened.classId,'');assert.equal(opened.startsOn,'');assert.equal(opened.tuitionDue,12000000);
});

test('all four registered courses populate their name fee and duration without any training catalog records',()=>{
 const options=fixtures();options.courses=[];
 for(let index=0;index<catalog.length;index++){
  const values=initialTrainingContractValues('AddTrainingContract',{consultationId:'admission-'+index,tuitionDue:0,depositDue:0},options);
  const selected=trainingContractSelection(values,options),field=trainingContractCourseField(courseField,selected);
  assert.equal(values.courseId,'');assert.equal(values.tuitionDue,catalog[index].tuitionFee);
  assert.equal(values.studentEmail,'learner'+index+'@gmail.com');assert.equal(values.depositDue,0);
  assert.equal(selected.durationMonths,catalog[index].durationMonths);assert.equal(selected.tuitionFee,catalog[index].tuitionFee);
  assert.equal(field.type,'text');assert.equal(field.readOnly,true);assert.equal(field.required,false);assert.equal(field.displayValue,catalog[index].name);
 }
});

test('a unique scoped matching training course is an optional real id link while the registered name stays read-only',()=>{
 const options=fixtures();
 for(let index=0;index<catalog.length;index++){
  const values=initialTrainingContractValues('AddTrainingContract',{consultationId:'admission-'+index},options);
  assert.equal(values.courseId,'course-'+index);assert.equal(values.tuitionDue,catalog[index].tuitionFee);
  const field=trainingContractCourseField(courseField,trainingContractSelection(values,options));
  assert.equal(field.type,'text');assert.equal(field.displayValue,catalog[index].name);assert.equal(field.readOnly,true);
 }
});

test('duplicate training offerings do not require selection or produce an arbitrary course id',()=>{
 const options=fixtures();options.courses.push({...options.courses[0],id:'another-laptop-course'});
 const values=initialTrainingContractValues('AddTrainingContract',{consultationId:'admission-0'},options);
 const selected=trainingContractSelection(values,options),field=trainingContractCourseField(courseField,selected);
 assert.equal(values.courseId,'');assert.equal(values.tuitionDue,14200000);
 assert.equal(selected.registeredName,catalog[0].name);assert.equal(selected.durationMonths,4);
 assert.equal(field.required,false);assert.equal(field.type,'text');assert.equal(field.displayValue,catalog[0].name);
});

test('switching admissions replaces the previous course fee and email and removes the former account binding',()=>{
 const options=fixtures(),before={consultationId:'admission-0',courseId:'course-0',tuitionDue:9000000,studentEmail:'old@gmail.com',studentId:99,terms:'Giữ điều khoản'};
 const next=changeTrainingContractValue(before,'consultationId','admission-3',options);
 assert.equal(next.courseId,'course-3');assert.equal(next.tuitionDue,21900000);assert.equal(next.studentEmail,'learner3@gmail.com');
 assert.equal(next.studentId,undefined);assert.equal(next.terms,before.terms);assert.equal(before.courseId,'course-0');
 options.courses=[];
 const unlinked=changeTrainingContractValue(next,'consultationId','admission-1',options);
 assert.equal(unlinked.courseId,'');assert.equal(unlinked.tuitionDue,13000000);
 assert.equal(trainingContractCourseField(courseField,trainingContractSelection(unlinked,options)).displayValue,catalog[1].name);
 const cleared=changeTrainingContractValue(next,'consultationId','',options);
 assert.equal(cleared.courseId,'');assert.equal(cleared.tuitionDue,'');assert.equal(cleared.studentEmail,'');
});

test('optional course links accept normalized exact names and skip unrelated or closed offerings',()=>{
 const options=fixtures();options.courses[0].name=('  '+catalog[0].name.toUpperCase().replaceAll(' ','  ')+'  ').normalize('NFD');
 options.courses.push({...options.courses[0],id:'closed-laptop',status:'CLOSED'},{...options.courses[0],id:'different-laptop',name:catalog[0].name+' nâng cao'});
 const values=initialTrainingContractValues('AddTrainingContract',{consultationId:'admission-0'},options);
 assert.equal(values.courseId,'course-0');assert.deepEqual(trainingContractSelection(values,options).courses.map(row=>row.id),['course-0']);
 options.courses=options.courses.filter(row=>row.id!=='course-0');
 const withoutMatch=initialTrainingContractValues('AddTrainingContract',{consultationId:'admission-0'},options);
 assert.equal(withoutMatch.courseId,'');assert.equal(withoutMatch.tuitionDue,14200000);
});

test('opening an existing contract preserves its saved course and negotiated tuition',()=>{
 const options=fixtures(),record={id:'contract',consultationId:'admission-0',courseId:'course-0',tuitionDue:12000000,studentEmail:'learner0@gmail.com',studentId:99};
 const opened=initialTrainingContractValues('EditTrainingContract',record,options);
 assert.deepEqual(opened,record);assert.notEqual(opened,record);
 const selection=trainingContractSelection(opened,options);assert.equal(selection.tuitionFee,14200000);assert.equal(opened.tuitionDue,12000000);
 const changed=changeTrainingContractValue(opened,'consultationId','admission-2',options);
 assert.equal(changed.courseId,'course-2');assert.equal(changed.tuitionDue,11000000);
});

test('historical linked ids do not replace the registered course presentation or overwrite negotiated fees',()=>{
 const options=fixtures();options.courses=[];
 const record={id:'old-contract',consultationId:'admission-0',courseId:'historical-course',tuitionDue:9500000,terms:'Điều khoản cũ'};
 let values=initialTrainingContractValues('EditTrainingContract',record,options);
 values=changeTrainingContractValue(values,'terms','Điều khoản đã cập nhật',options);
 values=changeTrainingContractValue(values,'tuitionDue',9000000,options);
 const field=trainingContractCourseField(courseField,trainingContractSelection(values,options));
 assert.equal(values.courseId,'historical-course');assert.equal(values.tuitionDue,9000000);assert.equal(values.terms,'Điều khoản đã cập nhật');
 assert.equal(field.displayValue,catalog[0].name);assert.equal(field.readOnly,true);assert.equal(field.required,false);
 const changedAdmission=changeTrainingContractValue(values,'consultationId','admission-1',options);
 assert.equal(changedAdmission.courseId,'');assert.equal(changedAdmission.tuitionDue,13000000);
});

test('manual tuition changes and unrelated form edits are preserved until an actual course or admission change',()=>{
 const options=fixtures();let values=initialTrainingContractValues('AddTrainingContract',{consultationId:'admission-0'},options);
 values=changeTrainingContractValue(values,'tuitionDue',12500000,options);
 values=changeTrainingContractValue(values,'terms','Điều khoản đã thỏa thuận',options);
 values=changeTrainingContractValue(values,'consultationId','admission-0',options);
 values=changeTrainingContractValue(values,'courseId','course-0',options);
 assert.equal(values.tuitionDue,12500000);assert.equal(trainingContractSelection(values,options).tuitionFee,14200000);
});

test('legacy and Excel admissions without a registered course retain manual selection and use the selected course fee',()=>{
 const options=fixtures();options.admissions.push({id:'excel',email:' Imported@GMAIL.COM ',intakeSource:'EXCEL'});
 const initial=changeTrainingContractValue({consultationId:'admission-0',courseId:'course-0',tuitionDue:14200000},'consultationId','excel',options);
 assert.equal(initial.courseId,'');assert.equal(initial.tuitionDue,'');assert.equal(initial.studentEmail,'imported@gmail.com');
 const selection=trainingContractSelection(initial,options);assert.equal(selection.courses.length,4);
 assert.deepEqual(trainingContractCourseField(courseField,selection),courseField);
 const manual=changeTrainingContractValue(initial,'courseId','course-1',options);
 assert.equal(manual.courseId,'course-1');assert.equal(manual.tuitionDue,1);
});
