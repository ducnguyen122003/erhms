import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {forms} from '../src/screens/definitions.js';
import {computedContractTemplateData,contractPdfFieldErrors,contractTemplateValue,editableTemplateData,initialContractTemplateData,isComputedTemplateField,resetContractTemplateData,templateSectionFilled,visibleTemplateFields} from '../src/screens/contractTemplate.js';
import {changeTrainingContractValue,initialTrainingContractValues,trainingContractSelection} from '../src/screens/trainingContract.js';

const name='Sửa chữa máy tính Laptop chuyên nghiệp';
const metadata={title:'Hợp đồng đào tạo',fixedValues:{agreementPlace:'Trung tâm dậy nghề Bách Khoa'},sections:[
 {key:'student',title:'Thông tin học viên',fields:[{key:'studentName',label:'Họ và tên',type:'text',readOnly:true,requiredForPdf:true},{key:'studentBirthDate',label:'Ngày sinh',type:'date',requiredForPdf:true}]},
 {key:'guardian',title:'Người bảo lãnh',optional:true,fields:[{key:'guardianName',label:'Họ tên người bảo lãnh',type:'text'}]}
]};
const options=()=>({contractTemplate:metadata,contractTemplateDefaults:{centerName:'Trung tâm đào tạo'},enrollmentCourseCatalog:[{name,durationMonths:4,tuitionFee:14200000}],courses:[],admissions:[
 {id:'first',fullName:'Học viên một',email:'FIRST@gmail.com',phone:'0912345678',enrollmentCourse:name,contractTemplateDefaults:{studentBirthDate:'2000-01-02',guardianName:'Gia đình một',module1Name:'Học phần mới'}},
 {id:'second',fullName:'Học viên hai',email:'second@gmail.com',enrollmentCourse:name,contractTemplateDefaults:{studentBirthDate:'2002-03-04',guardianName:''}}
]});

test('saved flat template data overrides admission and center defaults including deliberate blank and zero values',()=>{
 const config=options(),values={consultationId:'first',templateData:{centerName:'Trung tâm theo hợp đồng',studentBirthDate:'',module1TheoryHours:0}};
 const data=initialContractTemplateData(values,config,config.admissions[0]);
 assert.equal(data.centerName,'Trung tâm theo hợp đồng');assert.equal(data.studentBirthDate,'');assert.equal(data.module1TheoryHours,0);
 assert.equal(data.guardianName,'Gia đình một');assert.equal(data.module1Name,'Học phần mới');
 assert.equal(config.admissions[0].contractTemplateDefaults.studentBirthDate,'2000-01-02');
});

test('opening and reopening a draft preserves all entered template blanks without changing negotiated tuition',()=>{
 const config=options(),record={id:'contract',consultationId:'first',tuitionDue:12500000,templateData:{centerName:'Trung tâm đã lưu',studentBirthDate:'',guardianName:'',module5AssessmentHours:0,fees9Notes:'Thỏa thuận riêng'}};
 const opened=initialTrainingContractValues('EditTrainingContract',record,config);
 assert.equal(opened.tuitionDue,12500000);assert.equal(opened.templateData.studentBirthDate,'');assert.equal(opened.templateData.guardianName,'');
 assert.equal(opened.templateData.module5AssessmentHours,0);assert.equal(opened.templateData.fees9Notes,'Thỏa thuận riêng');
 assert.deepEqual(initialTrainingContractValues('EditTrainingContract',opened,config).templateData,opened.templateData);
});

test('switching students removes the former identity guardian emergency and module values while retaining center edits',()=>{
 const config=options(),values={consultationId:'first',tuitionDue:12500000,templateData:{centerName:'Tên trung tâm đã chỉnh',centerPhone:'0900000000',agreementPlace:'Đà Nẵng',studentIdentityNumber:'OLD-ID',studentAddress:'Địa chỉ cũ',emergencyPhone:'0987654321',guardianName:'Người cũ',module5Name:'Học phần cũ'}};
 const next=changeTrainingContractValue(values,'consultationId','second',config);
 assert.equal(next.templateData.centerName,'Tên trung tâm đã chỉnh');assert.equal(next.templateData.centerPhone,'0900000000');assert.equal(next.templateData.agreementPlace,undefined);
 assert.equal(next.templateData.studentBirthDate,'2002-03-04');assert.equal(next.templateData.guardianName,'');
 for(const key of ['studentIdentityNumber','studentAddress','emergencyPhone','module5Name'])assert.equal(next.templateData[key],undefined,key);
 assert.equal(next.tuitionDue,14200000);assert.equal(values.templateData.studentIdentityNumber,'OLD-ID');
 const cleared=resetContractTemplateData(values,config,null);assert.equal(cleared.studentIdentityNumber,undefined);assert.equal(cleared.centerName,'Tên trung tâm đã chỉnh');
});

test('computed contract fields reflect the current authoritative values and cannot be saved as stale template overrides',()=>{
 const config=options(),values={code:'HD-000123',consultationId:'first',tuitionDue:12500000,depositDue:0,startsOn:'2026-12-01',paymentDueOn:'2026-11-30',templateData:{studentName:'Tên cũ',tuitionDue:14200000,courseName:'Khóa cũ',studentBirthDate:'2000-01-02'}};
 const selection=trainingContractSelection(values,config);
 const expected={contractNumber:'HD-000123',studentName:'Học viên một',studentEmail:'first@gmail.com',studentPhone:'0912345678',courseName:name,durationMonths:4,tuitionDue:12500000,depositDue:0,startsOn:'2026-12-01',paymentDueOn:'2026-11-30'};
 for(const [key,value] of Object.entries(expected)){
  assert.equal(contractTemplateValue({key},values,selection),value,key);assert.equal(isComputedTemplateField({key}),true,key);
 }
 assert.deepEqual(editableTemplateData(values.templateData),{studentBirthDate:'2000-01-02'});
});

test('planned class dates override editable template dates and remain read-only',()=>{
 const values={templateData:{endsOn:'2026-01-01',latestOpeningOn:'2026-01-02',studentBirthDate:'2000-01-01'}};
 const selection={selectedClass:{startsOn:'2027-03-01',endsOn:'2027-07-31'}};
 assert.equal(contractTemplateValue({key:'endsOn'},values,selection),'2027-07-31');
 assert.equal(contractTemplateValue({key:'latestOpeningOn'},values,selection),'2027-03-01');
 assert.equal(isComputedTemplateField({key:'endsOn'}),true);
 assert.equal(isComputedTemplateField({key:'latestOpeningOn'}),true);
 assert.deepEqual(editableTemplateData(values.templateData),{studentBirthDate:'2000-01-01'});
});

test('section progress counts entered values and leaves incomplete PDF-required fields valid for draft data',()=>{
 const config=options(),values={consultationId:'first',templateData:{studentBirthDate:''}};
 assert.equal(templateSectionFilled(metadata.sections[0],values,trainingContractSelection(values,config)),1);
 assert.equal(templateSectionFilled(metadata.sections[1],values,trainingContractSelection(values,config)),0);
 assert.deepEqual(editableTemplateData({studentBirthDate:''}),{studentBirthDate:''});
});

test('computed fields retain unchanged saved snapshots and update when a different admission or manual course is chosen',()=>{
 const config=options(),record={id:'contract',consultationId:'first',courseId:null,registeredCourseName:'Khóa đã lưu',registeredDurationMonths:7,applicantName:'Tên trên hợp đồng',applicantPhone:'0900000000',studentEmail:'saved@gmail.com'};
 let selection={...trainingContractSelection(record,config),savedRecord:record};
 assert.equal(contractTemplateValue({key:'durationMonths'},record,selection),7);
 assert.equal(contractTemplateValue({key:'courseName'},record,selection),'Khóa đã lưu');
 assert.equal(contractTemplateValue({key:'studentName'},record,selection),'Tên trên hợp đồng');
 const changed=changeTrainingContractValue(record,'consultationId','second',config);
 selection={...trainingContractSelection(changed,config),savedRecord:record};
 assert.equal(contractTemplateValue({key:'durationMonths'},changed,selection),4);
 assert.equal(contractTemplateValue({key:'courseName'},changed,selection),name);
 assert.equal(contractTemplateValue({key:'studentName'},changed,selection),'Học viên hai');
});

test('missing PDF field summaries use document labels and retain server messages',()=>{
 const issues=contractPdfFieldErrors({'templateData.studentBirthDate':'Cần nhập ngày sinh','templateData.guardianName':'Cần nhập thông tin','unexpected':'Kiểm tra hợp đồng'},metadata);
 assert.deepEqual(issues.map(item=>item.label),['Ngày sinh','Họ tên người bảo lãnh','Thông tin hợp đồng']);
 assert.equal(issues[0].message,'Cần nhập ngày sinh');
});

test('prefilled template information is not editable or required from the manager',()=>{
 const actual=JSON.parse(readFileSync(new URL('../../G5_BE/src/main/resources/contracts/training-contract-fields.json',import.meta.url),'utf8'));
 assert.equal(actual.fixedValues.centerAddress,'Hà Nội');assert.ok(actual.fixedValues.centerBankAccount);
 const fields=actual.sections.flatMap(section=>section.fields),keys=new Set(fields.map(field=>field.key));
 for(const field of fields.filter(field=>Object.hasOwn(actual.fixedValues,field.key)))assert.equal(field.readOnly,true,field.key);
 for(const key of ['hasGuardian','module1Name','grossTuition','otherChargesDue','regulationReference'])assert.equal(keys.has(key),false,key);
 const field=fields.find(field=>field.key==='agreementPlace');
 assert.equal(isComputedTemplateField(field),true);
 const values={templateData:{agreementPlace:'Địa điểm cũ'}};
 assert.equal(contractTemplateValue(field,values,{},actual.fixedValues),'Trung tâm dậy nghề Bách Khoa');
 assert.equal(templateSectionFilled(actual.sections[0],values,{},actual.fixedValues),1);
 const editable=editableTemplateData({agreementPlace:'Địa điểm cũ',centerAddress:'Không dùng giá trị giả',centerBankAccount:'FAKE',studentIdentityNumber:'ID-001',initialTotal:1},actual);
 assert.deepEqual(editable,{studentIdentityNumber:'ID-001'});
});

test('the revised two-row fee table totals tuition and deposit without obsolete extra charges',()=>{
 const values={tuitionDue:13500000,depositDue:500000,templateData:{otherChargesDue:200000,fee7SeparateAmount:100000,module1TheoryHours:10}};
 const calculated=computedContractTemplateData(values);
 assert.deepEqual(calculated,{initialTotal:'14000000',initialTotalInWords:'Mười bốn triệu đồng'});
});

test('the free-text payment schedule is removed from both contract forms',()=>{
 for(const code of ['AddTrainingContract','EditTrainingContract']){
  assert.equal(forms[code].some(field=>field.key==='paymentSchedule'),false,code);
  assert.equal(forms[code].some(field=>field.key==='paymentDueOn'),true,code);
 }
});
