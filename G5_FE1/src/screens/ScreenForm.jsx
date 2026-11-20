import {useEffect,useRef,useState} from 'react';
import {Check,Upload} from 'lucide-react';
import {api} from '../api';
import {AdminAside,AdminMessage} from '../admin/AdminUI';
import {defaults,display} from './definitions';
import ProfileDocuments from './ProfileDocuments';
import {consultationFields,consultationPayload,newConsultationValues} from './admissionConsultation';
import {changeTrainingContractValue,initialTrainingContractValues,isTrainingContract,trainingContractCourseField,trainingContractSelection} from './trainingContract';
import ContractTemplateForm from './ContractTemplateForm';
import {editableTemplateData} from './contractTemplate';
import {signingReviewFields} from './contractSigning';
import ContractSigningDetails from './ContractSigningDetails';
import {classPlanPreview,classPlanYear,classPlanYears,weekdayChoices,weeklyPlanLabel} from './classPlanning';
import './class-management.css';

export function useUnsavedChanges(){
 const dirty=useRef(false);
 useEffect(()=>{const originalUrl=window.location.href,originalHistoryState=window.history.state;const before=event=>{if(dirty.current){event.preventDefault();event.returnValue='';}};const navigating=event=>{if(dirty.current&&!window.confirm('Bạn có thay đổi chưa lưu. Bỏ thay đổi và chuyển trang?'))event.preventDefault();};const pop=event=>{if(dirty.current&&!window.confirm('Bạn có thay đổi chưa lưu. Bỏ thay đổi và chuyển trang?')){event.stopImmediatePropagation();window.history.pushState(originalHistoryState,'',originalUrl);}};window.addEventListener('beforeunload',before);window.addEventListener('erhms-before-navigate',navigating);window.addEventListener('popstate',pop,true);return()=>{window.removeEventListener('beforeunload',before);window.removeEventListener('erhms-before-navigate',navigating);window.removeEventListener('popstate',pop,true);};},[]);
 return dirty;
}
export function localDateTime(value){if(!value)return '';const date=new Date(value);return Number.isNaN(date.valueOf())?'':new Date(date.getTime()-date.getTimezoneOffset()*60000).toISOString().slice(0,16);}
export function prepareValues(values,fields){const output={...values};for(const f of fields){if(f.type==='datetime-local')output[f.key]=values[f.key]?new Date(values[f.key]).toISOString():'';if(f.type==='number'&&values[f.key]!==''&&values[f.key]!==null&&values[f.key]!==undefined)output[f.key]=Number(values[f.key]);if(f.type==='checkbox')output[f.key]=Boolean(values[f.key]);}return output;}
export function optionLabel(option){return [option.code||option.assetCode,option.name||option.fullName||option.studentName||option.title].filter(Boolean).join(' · ')||option.id;}
export function Evidence({value}){const match=/^file:([0-9a-f-]{36}):(.+)$/i.exec(value||'');return match?<a className="screen-file-link" href={'/api/workspace/files/'+match[1]} download>{match[2]}</a>:<span>{value||'Chưa có minh chứng'}</span>;}
export function Fields({fields,values,setValue,options={},errors={},code,disabled,onUploading}){
 const [uploadError,setUploadError]=useState('');
 async function upload(event,key){const file=event.target.files?.[0];if(!file)return;setUploadError('');onUploading?.(true);try{if(file.size>2097152)throw new Error('Tệp không được vượt quá 2 MB');const data=new FormData();data.append('file',file);data.append('screen',code);const saved=await api('/api/workspace/files',{method:'POST',body:data});setValue(key,saved.reference);}catch(error){setUploadError(error.message);}finally{onUploading?.(false);event.target.value='';}}
 return <><div className="admin-field-grid">{fields.map(f=>{
  const id='screen-field-'+f.key,error=errors[f.key];let value=values[f.key]??'',choices=Array.isArray(f.source)?f.source:options[f.source]||[];
  if(f.displayValue!==undefined)value=f.displayValue;
  if(['AddTrainingContract','EditTrainingContract'].includes(code)&&f.key==='studentEmail')value=options.admissions?.find(row=>row.id===values.consultationId)?.email?.trim().toLowerCase()||value;
  if(f.source==='students'&&values.classId)choices=choices.filter(row=>!row.classId||row.classId===values.classId);
  if(f.source==='students'&&values.consultationId){const admission=options.admissions?.find(row=>row.id===values.consultationId);if(admission?.email)choices=choices.filter(row=>row.email?.trim().toLowerCase()===admission.email.trim().toLowerCase());}
  if(['AddClass','EditClass'].includes(code)&&f.source==='lecturers')choices=choices.filter(row=>!row.courseId||String(row.courseId)===String(values.courseId));
  const shared={id,name:f.key,required:f.required,disabled:disabled||f.disabled,readOnly:f.readOnly,'aria-invalid':Boolean(error),'aria-describedby':error?id+'-error':undefined};
  if(f.type==='weekdays')return <fieldset key={f.key} className="class-weekdays screen-field-wide" disabled={disabled||f.disabled}><legend>{f.label} *</legend><div>{weekdayChoices.map(day=><label key={day.id}><input type="checkbox" name={f.key} value={day.id} checked={(Array.isArray(value)?value:[]).map(Number).includes(day.id)} onChange={event=>setValue(f.key,event.target.checked?[...(Array.isArray(value)?value:[]),day.id].map(Number).sort((a,b)=>a-b):(Array.isArray(value)?value:[]).filter(current=>Number(current)!==day.id))}/>{day.name}</label>)}</div>{error&&<small className="field-error" id={id+'-error'}>{error}</small>}</fieldset>;
  return <label className={'admin-field'+(f.wide||['textarea','multi','checkbox','evidence'].includes(f.type)?' screen-field-wide':'')} key={f.key} htmlFor={id}>{f.label}{f.required&&f.type!=='checkbox'?' *':''}
   {f.type==='select'?<select {...shared} value={value} onChange={e=>setValue(f.key,e.target.value)}><option value="">Chọn {f.label.toLowerCase()}</option>{choices.map((item,index)=>{const val=typeof item==='string'?item:item.id;return <option key={String(val)+'-'+index} value={val} disabled={Boolean(item?.disabled)}>{typeof item==='string'?display(item):optionLabel(item)+(f.source==='classes'&&classPlanYear(item)?' · Năm '+classPlanYear(item):'')}</option>;})}</select>
   :f.type==='multi'?<select {...shared} multiple size={Math.min(6,Math.max(3,choices.length))} value={(Array.isArray(value)?value:[]).map(v=>String(v.id))} onChange={e=>setValue(f.key,[...e.target.selectedOptions].map(o=>({id:o.value})))}>{choices.map((item,index)=><option key={item.id+'-'+index} value={item.id}>{optionLabel(item)}{item.code&&item.name?' · '+item.code:''}</option>)}</select>
   :f.type==='textarea'?<textarea {...shared} rows={4} maxLength={f.maxLength||4000} value={value} onChange={e=>setValue(f.key,e.target.value)}/>
   :f.type==='checkbox'?<span className="screen-checkbox"><input {...shared} type="checkbox" checked={Boolean(value)} onChange={e=>setValue(f.key,e.target.checked)}/>Xác nhận</span>
   :f.type==='evidence'?<><input {...shared} readOnly={f.uploadOnly||f.readOnly} type="text" maxLength={1000} value={value} placeholder={f.uploadOnly?'Chọn tệp bản scan bên dưới':'Tham chiếu hồ sơ hoặc tải minh chứng'} onChange={e=>setValue(f.key,e.target.value)}/><span className="screen-upload"><Upload size={14}/><span>Tải tệp PDF / ảnh, tối đa 2 MB</span><input type="file" accept="application/pdf,image/png,image/jpeg" required={f.uploadOnly&&!value} disabled={disabled} onChange={event=>upload(event,f.key)}/></span>{value&&<Evidence value={value}/>}</>
   :<input {...shared} type={f.type} min={f.min} max={f.max} step={f.step} placeholder={f.placeholder} maxLength={f.type==='number'?undefined:f.maxLength||1000} value={value} onChange={e=>setValue(f.key,e.target.value)}/>}
   {f.type==='select'&&!Array.isArray(f.source)&&choices.length===0&&<small className="admin-muted">Chưa có lựa chọn trong phạm vi được giao.</small>}{f.type==='multi'&&<small className="admin-muted">Giữ Ctrl / Command để chọn nhiều mục.</small>}
   {error&&<small className="field-error" id={id+'-error'}>{error}</small>}
  </label>;
 })}</div>{uploadError&&<AdminMessage error={uploadError}/>}</>;
}
function initialValues(code,fields,record,related,options){const next=code==='AdmissionConsultation'?newConsultationValues(record):{...defaults,...record};for(const f of fields){if(f.type==='datetime-local')next[f.key]=localDateTime(next[f.key]);if(f.type==='checkbox')next[f.key]=Boolean(next[f.key]);}
  if(['AddAsset','EditAsset'].includes(code))next.status=record?.status||'AVAILABLE';
  if(code==='AddClass'){next.status='PLANNED';next.capacity='';next.planYear=new Date().getFullYear();next.weekdays=[];next.startTime='08:00';next.endTime='11:00';}
  if(code==='TimeTable_EditSession'&&!record?.id)next.changeNote='Xếp lịch học mới';
  if(code==='AssetHandover'){next.receivedAt=localDateTime(new Date().toISOString());next.signedBy=record?.studentName||'';next.assets=[];}
  if(code==='AssetMaintenance'&&record?.id)next.assetId=record.id;
  if(code==='Attendance_Correction'){next.sessionId=record?.id;next.studentId=new URLSearchParams(window.location.search).get('studentId')||'';}
  if(code==='PaymentInstruction'){next.content=related.instruction?.[0]?.content||'';next.version=related.instruction?.[0]?.version??0;}
  if(code==='DepositRefund')next.recipient=record?.studentName||'';
  if(code==='ContractSignature'){next.decision='APPROVE';next.reviewNote='';}
  next.assessments=related.assessments||[];next.lecturers=(related.lecturers||[]).map(row=>({id:row.id}));
  const params=new URLSearchParams(window.location.search);for(const key of ['classId','consultationId'])if(params.has(key)&&!(code==='EditTrainingContract'&&key==='consultationId'))next[key]=params.get(key);
  return isTrainingContract(code)?initialTrainingContractValues(code,next,options):next;
}
export default function ScreenForm({code,fields,record,related={},options,user,busy,error,errors,notice,onSave,onBack,saveLabel,extra,submitDisabled=false,onLogout}){
 const dirty=useUnsavedChanges(),[values,setValues]=useState(()=>initialValues(code,fields,record,related,options)),[uploading,setUploading]=useState(false),[planError,setPlanError]=useState(''),[contractYear,setContractYear]=useState('');const ref=useRef(null);

 const setValue=(key,value)=>{dirty.current=true;setPlanError('');if(isTrainingContract(code)&&key==='consultationId')setContractYear('');setValues(current=>{
  if(isTrainingContract(code))return changeTrainingContractValue(current,key,value,options);
  const next={...current,[key]:value};
  if(code==='AdmissionConsultation'&&key==='status'){
   next.appointmentAt='';next.documentDueOn='';next.discontinuationReason='';
  }
  if(code==='AddClass'&&key==='courseId'){
   const previous=options.courses?.find(row=>String(row.id)===String(current.courseId)),course=options.courses?.find(row=>String(row.id)===String(value));
   next.lecturerId='';next.capacity=course?.classCapacity??'';
   if(!current.name||current.name===previous?.name)next.name=course?.name||'';
  }
  return next;
 });};
 async function submit(event){event.preventDefault();if(busy||uploading||submitDisabled||fixedSession)return;if(code==='AddClass'&&(!planPreview||!planPreview.count)){setPlanError('Chọn năm, khoảng ngày, ngày học và khung giờ hợp lệ. Kế hoạch cần có ít nhất một buổi học.');return;}const prepared=isTrainingContract(code)&&values.templateData?{...values,templateData:editableTemplateData(values.templateData,options.contractTemplate)}:values;const data=prepareValues(code==='AdmissionConsultation'?consultationPayload(prepared):prepared,visibleFields);const ok=await onSave(data,()=>{dirty.current=false;});if(ok)dirty.current=false;}
 const contractSelection=isTrainingContract(code)?{...trainingContractSelection(values,options),savedRecord:record}:null;
 const formOptions={...options,...(contractSelection?{courses:contractSelection.courses,classes:contractSelection.classes.filter(row=>!contractYear||String(classPlanYear(row))===contractYear)}:{}),attendanceStudents:(related.attendance||[]).map(row=>({id:row.studentId,name:row.studentName}))};
 const selectedClass=code==='TimeTable_EditSession'?options.classes?.find(row=>String(row.id)===String(values.classId)):contractSelection?.selectedClass||(code==='EditClass'?record:null);
 const fixedSession=code==='TimeTable_EditSession'&&Boolean(record.planFixed||selectedClass?.planFixed),planPreview=code==='AddClass'?classPlanPreview(values):null;
 const selectedAdmission=contractSelection?.admission;
 const visibleFields=(code==='AdmissionConsultation'?consultationFields(fields,values):code==='ContractSignature'?signingReviewFields(fields,values):fields).map(field=>{
  if(contractSelection&&field.key==='courseId')return trainingContractCourseField(field,contractSelection);
  if(contractSelection&&field.key==='classId')return {...field,disabled:!contractSelection.admission};
  if(contractSelection&&field.key==='startsOn')return {...field,readOnly:true};
  if(code==='EditClass'&&field.key==='status')return {...field,source:field.source.map(status=>({id:status,name:display(status),disabled:status==='CANCELLED'&&Number(record.reservedCount)>0}))};
  if(code==='AddClass'&&field.key==='startsOn'&&Number(values.planYear)>=1900&&Number(values.planYear)<=9999)return {...field,min:values.planYear+'-01-01',max:values.planYear+'-12-31'};
  if(['AddClass','EditClass'].includes(code)&&field.key==='endsOn')return {...field,min:values.startsOn||undefined};
  if(code==='AddClass'&&field.key==='lecturerId')return {...field,disabled:!values.courseId};
  if(code==='AddClass'&&field.key==='endTime')return {...field,min:values.startTime||undefined};
  if(code==='TimeTable_EditSession'&&field.key==='classId'&&record.id)return {...field,disabled:true};
  if(selectedClass&&['startsAt','endsAt'].includes(field.key))return {...field,min:field.key==='endsAt'&&values.startsAt?values.startsAt:selectedClass.startsOn?selectedClass.startsOn+'T00:00':undefined,max:selectedClass.endsOn?selectedClass.endsOn+'T23:59':undefined};
  return field;
 });
 return <div className="admin-editor-grid"><form className="admin-card admin-form-card" ref={ref} onSubmit={submit} aria-busy={busy||uploading}><div className="admin-form-content"><h2>{code==='AddClass'?'Thông tin lớp học':'Thông tin cần cập nhật'}</h2><p className="admin-muted">Các trường có dấu * là bắt buộc.</p>
  {code==='AddClass'&&<div className="class-form-hint"><p>Mã lớp được cấp tự động, ví dụ L-000001. Giảng viên và phòng học là bắt buộc.</p><p>Lịch học được tạo một lần theo ngày học hằng tuần trong khoảng bắt đầu đến kết thúc. Ngày và khung giờ được chốt khi tạo lớp; kế hoạch năm sau cần tạo lớp riêng.</p></div>}
  {code==='AdmissionConsultation'&&<p className="admin-muted">Ghi nhận một lượt tư vấn mới. Để trống email nếu giữ email đã có trong hồ sơ.</p>}
  {selectedClass&&<div className="class-form-hint"><p><strong>{selectedClass.code} · {selectedClass.name}</strong>{classPlanYear(selectedClass)?' · Kế hoạch '+classPlanYear(selectedClass):''}</p><p>Bắt đầu học: {display(selectedClass.startsOn,'startsOn')}{selectedClass.endsOn?' · Kết thúc: '+display(selectedClass.endsOn,'endsOn'):''}</p><p>Giảng viên: {selectedClass.lecturerName||'Chưa phân công'}{selectedClass.classroomName?' · Phòng: '+selectedClass.classroomName:''}</p>{weeklyPlanLabel(selectedClass)&&<p>Lịch cố định: {weeklyPlanLabel(selectedClass)}</p>}{contractSelection&&selectedClass.remainingSlots!==undefined&&<p>Còn nhận: {selectedClass.remainingSlots} học viên</p>}</div>}
  {fixedSession&&<p className="class-form-hint">Buổi học thuộc kế hoạch đã chốt của lớp. Ngày, giờ và phòng học được giữ theo kế hoạch.</p>}
  {code==='EditClass'&&record.planFixed&&<p className="admin-muted">Chỉ điều chỉnh trạng thái, giảng viên và sĩ số. Ngày học, khung giờ và phòng học đã được chốt.</p>}
  {code==='EditClass'&&<p className="admin-muted">Chỉ hủy kế hoạch chưa có hợp đồng giữ chỗ hoặc học viên đã xếp lớp. Để sửa ngày và khung giờ, hủy lớp chưa sử dụng rồi tạo kế hoạch lớp mới.</p>}
  <AdminMessage error={error||planError} notice={notice}/>{selectedAdmission&&<div className="class-form-hint"><p><strong>Khóa học đăng ký: {selectedAdmission.enrollmentCourse||'Chưa cập nhật'}</strong></p><p>Khóa quan tâm ban đầu: {selectedAdmission.programName||'Chưa cập nhật'}</p>{(contractSelection.durationMonths||contractSelection.tuitionFee!==undefined)&&<p>{contractSelection.durationMonths?`Thời lượng tham khảo: ${contractSelection.durationMonths} tháng` :''}{contractSelection.durationMonths&&contractSelection.tuitionFee!==undefined?' · ':''}{contractSelection.tuitionFee!==undefined?`Học phí gốc: ${display(contractSelection.tuitionFee,'tuitionFee')}`:''}</p>}<p>Chọn lớp đã được Admin lập kế hoạch. Ngày bắt đầu lấy theo lớp; học phí có thể điều chỉnh theo thỏa thuận trước khi lưu hợp đồng.</p></div>}
  {contractSelection&&<label className="admin-field class-contract-year">Năm kế hoạch của lớp<select value={contractYear} disabled={busy||uploading} onChange={event=>{const year=event.target.value;setContractYear(year);if(selectedClass&&year&&String(classPlanYear(selectedClass))!==year)setValue('classId','');}}><option value="">Tất cả năm kế hoạch</option>{classPlanYears(contractSelection.classes).map(year=><option key={year} value={year}>{year}</option>)}</select></label>}
  {selectedAdmission&&contractSelection.classes.length===0&&<p className="admin-message">Chưa có lớp còn nhận học viên cho khóa đăng ký trong phạm vi phụ trách. Liên hệ Admin để lập kế hoạch lớp trước khi lưu hợp đồng.</p>}
  {code==='ContractSignature'&&<ContractSigningDetails record={record} onLogout={onLogout} showOriginal disabled={busy||uploading}/>}<fieldset disabled={busy||uploading||fixedSession}><Fields fields={visibleFields} values={values} setValue={setValue} options={formOptions} errors={errors} code={code} disabled={busy||fixedSession} onUploading={setUploading}/>
  {code==='AddClass'&&planPreview&&<div className="class-form-hint class-plan-preview" aria-live="polite"><p><strong>{planPreview.count} buổi học theo kế hoạch</strong></p>{planPreview.count>0&&<p>Buổi đầu: {display(planPreview.first,'startsOn')} · Buổi cuối: {display(planPreview.last,'endsOn')}</p>}<p>{weeklyPlanLabel(values)}</p></div>}
  {['AddCourse','EditCourse'].includes(code)&&<><h3>Cấu hình đánh giá</h3><p className="admin-muted">Tổng trọng số bằng 100%. Cấu hình này dùng để nhập điểm và kiểm tra kết quả khóa học.</p><div className="screen-assessments">{(values.assessments||[]).map((item,index)=><div key={index}><input aria-label={'Tên đầu điểm '+(index+1)} value={item.title||''} maxLength={160} required onChange={e=>setValue('assessments',values.assessments.map((row,i)=>i===index?{...row,title:e.target.value}:row))}/><input aria-label={'Trọng số '+(index+1)} type="number" min="0.01" max="100" step="0.01" value={item.weight??''} required onChange={e=>setValue('assessments',values.assessments.map((row,i)=>i===index?{...row,weight:e.target.value}:row))}/><button type="button" className="admin-text-button" onClick={()=>setValue('assessments',values.assessments.filter((_,i)=>i!==index))}>Xóa</button></div>)}</div><button type="button" className="admin-secondary" onClick={()=>setValue('assessments',[...(values.assessments||[]),{title:'',weight:''}])}>Thêm đầu điểm</button>{errors.assessments&&<p className="field-error">{errors.assessments}</p>}<h3>Giảng viên đủ điều kiện phân công</h3><Fields fields={[{key:'lecturers',label:'Giảng viên',type:'multi',source:'lecturers'}]} values={values} setValue={setValue} options={options} errors={errors} code={code}/></>}
  {['AddCourse','EditCourse'].includes(code)&&<p className="admin-muted">Quản lý phụ trách sẽ chọn được khóa học này khi lập hợp đồng và điều phối lớp.</p>}
  {contractSelection&&<ContractTemplateForm metadata={options.contractTemplate} values={values} selection={contractSelection} disabled={busy||uploading} errors={errors} onChange={(key,value)=>{dirty.current=true;setValues(current=>({...current,templateData:{...current.templateData,[key]:value}}));}}/>}
  {code==='SendNotification'&&<p className="admin-muted">Thông báo được gửi trong hệ thống; người nhận chỉ xem thông báo của mình.</p>}
  {['AddTrainingContract','EditTrainingContract'].includes(code)&&<p className="admin-muted">Sau khi lưu và hoàn tất thông tin, gửi hợp đồng cho tuyển sinh in và lấy chữ ký học viên. QLHV xác nhận bản scan đã ký rồi mới có thể cấp tài khoản theo email trên hợp đồng. Nội dung hợp đồng được chốt khi gửi tuyển sinh.</p>}
  {selectedAdmission&&<ProfileDocuments documents={selectedAdmission.profileDocuments||[]} legacyEvidence={selectedAdmission.profileEvidence} compact/>}
  {code==='EditStudentProfile'&&<p className="admin-muted">Email đăng nhập được quản trị tại phần Tài khoản người dùng.</p>}
  {extra}</fieldset></div><footer className="admin-form-footer"><button type="button" className="admin-secondary" disabled={busy||uploading} onClick={onBack}>Quay lại</button><button type="submit" disabled={busy||uploading||submitDisabled||fixedSession}><Check size={15}/>{uploading?'Đang tải minh chứng…':busy?'Đang lưu…':fixedSession?'Kế hoạch đã chốt':submitDisabled?'Chưa đủ điều kiện':saveLabel||'Lưu thông tin'}</button></footer></form><AdminAside user={user} tips={['Chỉ cập nhật dữ liệu trong phạm vi được phân công.','Kiểm tra thông tin và minh chứng trước khi lưu.']}/></div>;
}
