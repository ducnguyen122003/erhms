import {hasContractTemplate,initialContractTemplateData,resetContractTemplateData} from './contractTemplate.js';
import {contractClassChoices} from './classPlanning.js';

export const isTrainingContract=code=>['AddTrainingContract','EditTrainingContract'].includes(code);

export function normalizedCourseName(value){return String(value||'').normalize('NFC').trim().replace(/\s+/gu,' ').toLocaleLowerCase('vi-VN');}

export function trainingContractCourseField(field,selection){
 return selection.registeredName?{...field,type:'text',readOnly:true,required:false,displayValue:selection.registeredName}:field;
}

export function trainingContractSelection(values,options={}){
 const admission=options.admissions?.find(row=>String(row.id)===String(values.consultationId));
 const registeredName=admission?.enrollmentCourse?.trim()||'';
 const registeredKey=normalizedCourseName(registeredName);
 const available=(options.courses||[]).filter(row=>!row.status||['OPEN','ACTIVE'].includes(row.status));
 const courses=registeredName?available.filter(row=>normalizedCourseName(row.name)===registeredKey):available;
 const catalog=registeredName?options.enrollmentCourseCatalog?.find(row=>normalizedCourseName(row.name)===registeredKey):null;
 const course=courses.find(row=>String(row.id)===String(values.courseId));
 const classes=contractClassChoices(values,options),selectedClass=classes.find(row=>String(row.id)===String(values.classId));
 return {admission,registeredName,courses,course,catalog,classes,selectedClass,
  durationMonths:catalog?.durationMonths??course?.durationMonths,
  tuitionFee:catalog?.tuitionFee??course?.tuitionFee};
}

function admissionDefaults(values,consultationId,options){
 const next={...values,consultationId,courseId:'',tuitionDue:'',studentEmail:''};
 if(Array.isArray(options.classes)){next.classId='';next.startsOn='';}
 delete next.studentId;
 const selection=trainingContractSelection(next,options);
 if(hasContractTemplate(values,options,selection.admission))next.templateData=resetContractTemplateData(values,options,selection.admission);
 next.studentEmail=selection.admission?.email?.trim().toLowerCase()||'';
 if(selection.registeredName){
  if(selection.courses.length===1)next.courseId=selection.courses[0].id;
  next.tuitionDue=selection.catalog?.tuitionFee??(selection.courses.length===1?selection.courses[0].tuitionFee:'')??'';
 }
 return next;
}

export function initialTrainingContractValues(code,values,options={}){
 const next=code==='AddTrainingContract'?admissionDefaults(values,values.consultationId||'',options):{...values};
 if(code==='EditTrainingContract'&&Array.isArray(options.classes)&&!next.courseId){
  const {registeredName,courses}=trainingContractSelection(next,options);
  const savedClass=options.classes.find(row=>String(row.id)===String(next.classId));
  next.courseId=registeredName?(courses.length===1?courses[0].id:''):savedClass?.courseId??'';
 }
 const {selectedClass,admission}=trainingContractSelection(next,options);
 if(Array.isArray(options.classes)){
  next.classId=selectedClass?.id||'';
  next.startsOn=selectedClass?.startsOn||'';
 }
 if(hasContractTemplate(next,options,admission))next.templateData=initialContractTemplateData(next,options,admission);
 return next;
}

export function changeTrainingContractValue(values,key,value,options={}){
 const next={...values,[key]:value};
 if(String(values[key]??'')===String(value??''))return next;
 if(key==='consultationId')return admissionDefaults(next,value,options);
 if(key==='courseId'){
  const selection=trainingContractSelection(next,options);
  if(!selection.course)next.courseId='';
  next.tuitionDue=selection.registeredName?selection.catalog?.tuitionFee??selection.course?.tuitionFee??'':selection.course?.tuitionFee??'';
  if(Array.isArray(options.classes)){next.classId='';next.startsOn='';}
 }
 if(key==='classId'){
  const selection=trainingContractSelection(next,options),selected=selection.selectedClass;
  next.classId=selected?.id||'';next.startsOn=selected?.startsOn||'';
  if(selected&&values.tuitionDue==='')next.tuitionDue=selection.catalog?.tuitionFee??selection.course?.tuitionFee??'';
 }
 return next;
}

