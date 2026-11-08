import {useEffect,useRef,useState} from 'react';
import {Check,Upload} from 'lucide-react';
import {api} from '../api';
import {AdminAside,AdminMessage} from '../admin/AdminUI';
import {defaults,display} from './definitions';

export function useUnsavedChanges(){
 const dirty=useRef(false);
 useEffect(()=>{const originalUrl=window.location.href;const before=event=>{if(dirty.current){event.preventDefault();event.returnValue='';}};const navigating=event=>{if(dirty.current&&!window.confirm('Bạn có thay đổi chưa lưu. Bỏ thay đổi và chuyển trang?'))event.preventDefault();};const pop=event=>{if(dirty.current&&!window.confirm('Bạn có thay đổi chưa lưu. Bỏ thay đổi và chuyển trang?')){event.stopImmediatePropagation();window.history.pushState({},'',originalUrl);}};window.addEventListener('beforeunload',before);window.addEventListener('erhms-before-navigate',navigating);window.addEventListener('popstate',pop,true);return()=>{window.removeEventListener('beforeunload',before);window.removeEventListener('erhms-before-navigate',navigating);window.removeEventListener('popstate',pop,true);};},[]);
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
  const id='screen-field-'+f.key,value=values[f.key]??'',error=errors[f.key];let choices=Array.isArray(f.source)?f.source:options[f.source]||[];
  if(f.source==='students'&&values.classId)choices=choices.filter(row=>!row.classId||row.classId===values.classId);
  if(f.source==='students'&&values.consultationId){const admission=options.admissions?.find(row=>row.id===values.consultationId);if(admission?.email)choices=choices.filter(row=>row.email?.toLowerCase()===admission.email.toLowerCase());}
  const shared={id,name:f.key,required:f.required,disabled:disabled||f.disabled,readOnly:f.readOnly,'aria-invalid':Boolean(error),'aria-describedby':error?id+'-error':undefined};
  return <label className={'admin-field'+(['textarea','multi','checkbox','evidence'].includes(f.type)?' screen-field-wide':'')} key={f.key} htmlFor={id}>{f.label}{f.required&&f.type!=='checkbox'?' *':''}
   {f.type==='select'?<select {...shared} value={value} onChange={e=>setValue(f.key,e.target.value)}><option value="">Chọn {f.label.toLowerCase()}</option>{choices.map((item,index)=>{const val=typeof item==='string'?item:item.id;return <option key={String(val)+'-'+index} value={val}>{typeof item==='string'?display(item):optionLabel(item)}</option>;})}</select>
   :f.type==='multi'?<select {...shared} multiple size={Math.min(6,Math.max(3,choices.length))} value={(Array.isArray(value)?value:[]).map(v=>String(v.id))} onChange={e=>setValue(f.key,[...e.target.selectedOptions].map(o=>({id:o.value})))}>{choices.map((item,index)=><option key={item.id+'-'+index} value={item.id}>{optionLabel(item)}{item.code&&item.name?' · '+item.code:''}</option>)}</select>
   :f.type==='textarea'?<textarea {...shared} rows={4} maxLength={f.maxLength||4000} value={value} onChange={e=>setValue(f.key,e.target.value)}/>
   :f.type==='checkbox'?<span className="screen-checkbox"><input {...shared} type="checkbox" checked={Boolean(value)} onChange={e=>setValue(f.key,e.target.checked)}/>Xác nhận</span>
   :f.type==='evidence'?<><input {...shared} type="text" maxLength={1000} value={value} placeholder="Tham chiếu hồ sơ hoặc tải minh chứng" onChange={e=>setValue(f.key,e.target.value)}/><span className="screen-upload"><Upload size={14}/><span>Tải tệp PDF / ảnh, tối đa 2 MB</span><input type="file" accept="application/pdf,image/png,image/jpeg" disabled={disabled} onChange={event=>upload(event,f.key)}/></span>{value&&<Evidence value={value}/>}</>
   :<input {...shared} type={f.type} min={f.min} max={f.max} step={f.step} maxLength={f.type==='number'?undefined:f.maxLength||1000} value={value} onChange={e=>setValue(f.key,e.target.value)}/>}
   {f.type==='select'&&!Array.isArray(f.source)&&choices.length===0&&<small className="admin-muted">Chưa có lựa chọn trong phạm vi được giao.</small>}{f.type==='multi'&&<small className="admin-muted">Giữ Ctrl / Command để chọn nhiều mục.</small>}
   {error&&<small className="field-error" id={id+'-error'}>{error}</small>}
  </label>;
 })}</div>{uploadError&&<AdminMessage error={uploadError}/>}</>;
}
function initialValues(code,fields,record,related){const next={...defaults,...record};for(const f of fields){if(f.type==='datetime-local')next[f.key]=localDateTime(next[f.key]);if(f.type==='checkbox')next[f.key]=Boolean(next[f.key]);}
  if(['AddAsset','EditAsset'].includes(code))next.status=record?.status||'AVAILABLE';if(code==='AdmissionConsultation')next.status=record?.status==='NEW'?'CONTACTED':record?.status||'CONTACTED';
  if(code==='AssetHandover'){next.receivedAt=localDateTime(new Date().toISOString());next.signedBy=record?.studentName||'';next.assets=[];}
  if(code==='AssetMaintenance'&&record?.id)next.assetId=record.id;
  if(code==='Attendance_Correction'){next.sessionId=record?.id;next.studentId=new URLSearchParams(window.location.search).get('studentId')||'';}
  if(code==='PaymentInstruction'){next.content=related.instruction?.[0]?.content||'';next.version=related.instruction?.[0]?.version??0;}
  if(code==='DepositRefund')next.recipient=record?.studentName||'';
  next.assessments=related.assessments||[];next.lecturers=(related.lecturers||[]).map(row=>({id:row.id}));
  const params=new URLSearchParams(window.location.search);for(const key of ['classId','consultationId'])if(params.has(key))next[key]=params.get(key);
  return next;
}
export default function ScreenForm({code,fields,record,related={},options,user,busy,error,errors,notice,onSave,onBack,saveLabel,extra,submitDisabled=false}){
 const dirty=useUnsavedChanges(),[values,setValues]=useState(()=>initialValues(code,fields,record,related)),[uploading,setUploading]=useState(false);const ref=useRef(null);

 const setValue=(key,value)=>{dirty.current=true;setValues(current=>({...current,[key]:value}));};
 async function submit(event){event.preventDefault();if(busy||uploading||submitDisabled)return;const data=prepareValues(values,fields);const ok=await onSave(data,()=>{dirty.current=false;});if(ok)dirty.current=false;}
 const formOptions={...options,attendanceStudents:(related.attendance||[]).map(row=>({id:row.studentId,name:row.studentName}))};
 return <div className="admin-editor-grid"><form className="admin-card admin-form-card" ref={ref} onSubmit={submit} aria-busy={busy||uploading}><div className="admin-form-content"><h2>Thông tin cần cập nhật</h2><p className="admin-muted">Các trường có dấu * là bắt buộc.</p><AdminMessage error={error} notice={notice}/><fieldset disabled={busy||uploading}><Fields fields={fields} values={values} setValue={setValue} options={formOptions} errors={errors} code={code} disabled={busy} onUploading={setUploading}/>
  {['AddCourse','EditCourse'].includes(code)&&<><h3>Cấu hình đánh giá</h3><p className="admin-muted">Tổng trọng số bằng 100%. Cấu hình này dùng để nhập điểm và kiểm tra kết quả khóa học.</p><div className="screen-assessments">{(values.assessments||[]).map((item,index)=><div key={index}><input aria-label={'Tên đầu điểm '+(index+1)} value={item.title||''} maxLength={160} required onChange={e=>setValue('assessments',values.assessments.map((row,i)=>i===index?{...row,title:e.target.value}:row))}/><input aria-label={'Trọng số '+(index+1)} type="number" min="0.01" max="100" step="0.01" value={item.weight??''} required onChange={e=>setValue('assessments',values.assessments.map((row,i)=>i===index?{...row,weight:e.target.value}:row))}/><button type="button" className="admin-text-button" onClick={()=>setValue('assessments',values.assessments.filter((_,i)=>i!==index))}>Xóa</button></div>)}</div><button type="button" className="admin-secondary" onClick={()=>setValue('assessments',[...(values.assessments||[]),{title:'',weight:''}])}>Thêm đầu điểm</button>{errors.assessments&&<p className="field-error">{errors.assessments}</p>}<h3>Giảng viên đủ điều kiện phân công</h3><Fields fields={[{key:'lecturers',label:'Giảng viên',type:'multi',source:'lecturers'}]} values={values} setValue={setValue} options={options} errors={errors} code={code}/></>}
  {code==='SendNotification'&&<p className="admin-muted">Thông báo được gửi trong hệ thống; người nhận chỉ xem thông báo của mình.</p>}
  {code==='AddTrainingContract'&&<p className="admin-muted">Học viên cần có tài khoản Student với email khớp hồ sơ đã tư vấn. Quản trị viên tạo tài khoản trước khi lập hợp đồng.</p>}
  {code==='EditStudentProfile'&&<p className="admin-muted">Email đăng nhập được quản trị tại phần Tài khoản người dùng.</p>}
  {extra}</fieldset></div><footer className="admin-form-footer"><button type="button" className="admin-secondary" disabled={busy||uploading} onClick={onBack}>Quay lại</button><button type="submit" disabled={busy||uploading||submitDisabled}><Check size={15}/>{uploading?'Đang tải minh chứng…':busy?'Đang lưu…':submitDisabled?'Chưa đủ điều kiện':saveLabel||'Lưu thông tin'}</button></footer></form><AdminAside user={user} tips={['Chỉ cập nhật dữ liệu trong phạm vi được phân công.','Kiểm tra thông tin và minh chứng trước khi lưu.']}/></div>;
}
