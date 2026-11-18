export const consultationOutcomes=['NEEDS_COUNSELING','DOCUMENT_APPOINTMENT','NOT_PROCEEDING'];
export const managedAdmissionStates=['PROFILE_PENDING','PROFILE_CREATED','CONTRACTED'];

export function canRecordConsultation(status){return status!=='NOT_PROCEEDING'&&!managedAdmissionStates.includes(status);}

export function newConsultationValues(record={}){
 return {
  id:record.id,version:record.version??0,
  email:'',channel:'',status:managedAdmissionStates.includes(record.status)?record.status:'',
  notes:'',appointmentAt:'',documentDueOn:'',discontinuationReason:''
 };
}

export function consultationFields(fields,values){
 const status=values.status,managed=managedAdmissionStates.includes(status);
 return fields.filter(field=>{
  if(field.key==='appointmentAt')return status==='NEEDS_COUNSELING';
  if(field.key==='documentDueOn')return status==='DOCUMENT_APPOINTMENT';
  if(field.key==='discontinuationReason')return status==='NOT_PROCEEDING';
  if(field.key==='notes')return status!=='NOT_PROCEEDING';
  return true;
 }).map(field=>{
  if(field.key==='status')return {...field,source:managed?[status]:consultationOutcomes,disabled:managed};
  if(field.key==='notes')return {...field,label:status==='DOCUMENT_APPOINTMENT'?'Ghi chú':'Nội dung tư vấn',required:status==='NEEDS_COUNSELING'||managed};
  return field;
 });
}

export function consultationPayload(values){
 if(managedAdmissionStates.includes(values.status))return {...values};
 return {...values,
  appointmentAt:values.status==='NEEDS_COUNSELING'?values.appointmentAt||'':'',
  documentDueOn:values.status==='DOCUMENT_APPOINTMENT'?values.documentDueOn||'':'',
  discontinuationReason:values.status==='NOT_PROCEEDING'?values.discontinuationReason||'':'',
  notes:values.status==='NOT_PROCEEDING'?'':values.notes||''
 };
}
