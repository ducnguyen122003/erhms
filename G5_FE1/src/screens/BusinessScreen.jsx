import {useEffect,useRef,useState} from 'react';
import {ArrowLeft,ArrowUpRight,CalendarDays,Check,Download,Plus,RefreshCw,Search,ShieldCheck} from 'lucide-react';
import {api} from '../api';
import {dashboardRoles} from '../roles';
import {AdminDialog,AdminLoading,AdminMessage,AdminPage,AdminPagination,AdminStats} from '../admin/AdminUI';
import {canScreen,contractQueueReturnPath,screenPath,screens,screenTitle} from './access';
import {actionForms,columns,defaults,display,filterStates,forms,formBacks,labels,statuses,targets} from './definitions';
import ScreenForm,{Evidence,Fields,localDateTime,optionLabel,prepareValues,useUnsavedChanges} from './ScreenForm';
import './screens.css';
import './calendar.css';
import TimetableConflictNotice from './TimetableConflictNotice';
import AdmissionExcelImport from './AdmissionExcelImport';
import AdmissionDocumentUpload from './AdmissionDocumentUpload';
import ProfileDocuments from './ProfileDocuments';
import {admissionStatus} from './admissionDocuments';
import {admissionHome} from './admissionDashboard';
import {canRecordConsultation} from './admissionConsultation';
import ClassEnrollment from './ClassEnrollment';
import ClassSchedule from './ClassSchedule';
import ContractPdfActions from './ContractPdfActions';
import ContractSigningDetails from './ContractSigningDetails';
import {canProvisionContract,canReviewSignedScan,canSendContract} from './contractSigning';
import StudentContractPayment from './StudentContractPayment';
import StudentPaymentReview from './StudentPaymentReview';
import {DepositSettlementOverview,RefundReview,refundRequestFields,refundRequestPayload,refundRequestValues,refundTransferFields,refundTransferPayload} from './EquipmentRefund';
import {attendanceDetailSearch,attendanceReturnSearch} from './attendanceSchedule';
import ClosureConditions from './ClosureConditions';
import StudentRetakeOffer,{StudentRetakeList} from './StudentRetakeOffer';

const empty={},newForms=new Set(['AddCourse','AddClass','AddTrainingContract','AddAsset','AddEquipmentRequest','SendNotification','TimeTable_EditSession']);
const editableClass=classroom=>!['COMPLETED','CANCELLED'].includes(classroom.status);
function weekRange(value=new Date()){const monday=new Date(value);monday.setHours(12,0,0,0);monday.setDate(monday.getDate()-(monday.getDay()+6)%7);const sunday=new Date(monday);sunday.setDate(sunday.getDate()+6);const day=date=>new Date(date.getTime()-date.getTimezoneOffset()*60000).toISOString().slice(0,10);return {from:day(monday),to:day(sunday)};}
const sectionNames={history:'Lịch sử xử lý',enrollments:'Lớp học đang tham gia',contracts:'Hợp đồng liên quan',classes:'Các lớp học',students:'Danh sách học viên',sessions:'Lịch học',transactions:'Giao dịch theo hợp đồng',loans:'Biên bản thiết bị',maintenance:'Phiếu bảo trì',incidents:'Sự cố liên quan',settlements:'Kết quả quyết toán',assessments:'Cấu hình đầu điểm',lecturers:'Giảng viên đủ điều kiện',instruction:'Hướng dẫn thanh toán'};
sectionNames.signingHistory='Lịch sử ký hợp đồng';
function Badge({value}){return <span className={'admin-badge '+(['PENDING','NEW','NOT_PROCEEDING','DOCUMENTS_INCOMPLETE','MISMATCH','ABSENT','OPEN','RETURN_DUE','WITHDRAWN','FAILED','SENT_FOR_SIGNATURE','SIGNATURE_REVIEW'].includes(value)?'is-warning':'')}>{display(value)}</span>;}
function AdmissionBadges({record}){const primary=admissionStatus(record);return <div className="admission-status-badges"><Badge value={primary}/>{record.documentStatus&&primary!==record.documentStatus&&<Badge value={record.documentStatus}/>}</div>;}
function destination(result){const kind=screens[result.screen]?.kind;return screenPath(result.screen,['list','notifications','timetable','report'].includes(kind)?null:result.id);}
function Facts({record,showManager=true,hideEquipmentCatalogs=false}){return <dl className="screen-facts">{Object.entries(record).filter(([key,value])=>labels[key]&&value!==null&&value!==undefined&&!['status','state','content','profileEvidence'].includes(key)&&(showManager||key!=='managerName')&&!(hideEquipmentCatalogs&&['category','location','groupName'].includes(key))).map(([key,value])=><div key={key}><dt>{key==='signatureApprovedAt'&&record.signatureMethod==='RETAKE_ACCEPTANCE'?'Ngày xác nhận trực tuyến':labels[key]}</dt><dd>{/Evidence$|^evidence$|^approvalReference$/.test(key)?<Evidence value={value}/>:display(value,key)}</dd></div>)}</dl>;}
function RelatedTable({rows,title,onOpen}){if(!rows?.length)return <section className="screen-related"><h3>{title}</h3><p className="admin-muted">Chưa có dữ liệu được ghi nhận.</p></section>;const keys=[...new Set(rows.flatMap(Object.keys))].filter(key=>labels[key]).slice(0,7);return <section className="screen-related"><h3>{title}</h3><div className="admin-table-scroll"><table className="admin-table"><thead><tr>{keys.map(key=><th key={key}>{labels[key]}</th>)}{onOpen&&<th>Thao tác</th>}</tr></thead><tbody>{rows.map((row,index)=><tr key={row.id||index}>{keys.map(key=><td key={key}>{/Evidence$|^evidence$|^approvalReference$/.test(key)?<Evidence value={row[key]}/>:display(row[key],key)}</td>)}{onOpen&&<td><button type="button" className="admin-text-button" onClick={()=>onOpen(row)}>Xem<ArrowUpRight size={13}/></button></td>}</tr>)}</tbody></table></div></section>;}
function ActionEditor({title,action,fields,record,options,code,busy,errors,error,onSave,onClose}){
 const [values,setValues]=useState(()=>({...defaults,...record,returnedAt:localDateTime(new Date().toISOString()),assets:[],recipients:[]})),[uploading,setUploading]=useState(false);const dirty=useUnsavedChanges();
 const submit=event=>{event.preventDefault();onSave(prepareValues(values,fields),()=>{dirty.current=false;});};
 const submitLabel=action==='request-refund'?'Gửi kế toán':'Xác nhận';
 return <AdminDialog title={title} onClose={onClose} busy={busy||uploading}><form className="admin-dialog-body screen-action-form" onSubmit={submit} aria-busy={busy||uploading}><AdminMessage error={error}/>
  {action==='send-for-signature'&&<p className="admin-muted">Hoàn tất thông tin xuất PDF trước khi gửi. Nội dung và bản PDF được chốt khi gửi tuyển sinh để in, lấy chữ ký học viên.</p>}
  <fieldset disabled={busy||uploading}><Fields fields={fields} values={values} setValue={(key,value)=>{dirty.current=true;setValues(current=>({...current,[key]:value}));}} options={options} errors={errors} code={code} disabled={busy} onUploading={setUploading}/></fieldset><div className="admin-dialog-actions"><button type="button" className="admin-secondary" onClick={onClose} disabled={busy||uploading} data-initial-focus>Hủy</button><button type="submit" disabled={busy||uploading}><Check size={14}/>{busy||uploading?'Đang xử lý…':submitLabel}</button></div></form></AdminDialog>;
}

export default function BusinessScreen({code,accountId:id,user,functions=[],onNavigate,onLogout,onChanged,initialNotice='',listIntro=null,listRevision=0}){
 const spec=screens[code],[data,setData]=useState(null),[loading,setLoading]=useState(true),[error,setError]=useState(''),[notice,setNotice]=useState(initialNotice),[fieldErrors,setFieldErrors]=useState({}),[failureCode,setFailureCode]=useState(''),[revision,setRevision]=useState(0),[saving,setSaving]=useState(false),[action,setAction]=useState(null),[search,setSearch]=useState(()=>code==='PaymentStatus_Admissions'?(new URLSearchParams(window.location.search).get('q')||'').slice(0,200):''),[filter,setFilter]=useState(()=>({q:code==='PaymentStatus_Admissions'?(new URLSearchParams(window.location.search).get('q')||'').slice(0,200):'',status:'',year:'',page:0,from:'',to:'',...(screens[code].kind==='timetable'?weekRange():{})}));
 const controllerRef=useRef(null),pending=useRef(false),mounted=useRef(false);
 const [editingProfile,setEditingProfile]=useState(false);
 const isReport=spec.kind==='report',record=data?.record,related=data?.related||empty,options=data?.options||empty,can=target=>canScreen(target,user,functions);
 const refundContext=related.refundContext||empty,refundReview=related.refundReview?.[0],depositSettlement=related.settlements?.[0];
 const isEquipmentReturn=['ListAssetDetail','Recovery_Withdrawal'].includes(code);
 useEffect(()=>{mounted.current=true;return()=>{mounted.current=false;controllerRef.current?.abort();};},[]);
 useEffect(()=>{const timer=setTimeout(()=>setFilter(current=>current.q===search?current:{...current,q:search,page:0}),300);return()=>clearTimeout(timer);},[search]);
 useEffect(()=>{const controller=new AbortController();setLoading(true);setError('');const params=new URLSearchParams(filter);api('/api/workspace/screens/'+code+(id?'/'+id:'')+'?'+params,{signal:controller.signal}).then(result=>{if(!controller.signal.aborted)setData(result);}).catch(cause=>{if(!controller.signal.aborted){if(cause.status===401)onLogout();else setError(cause.message);}}).finally(()=>{if(!controller.signal.aborted)setLoading(false);});return()=>controller.abort();},[code,id,filter,revision,listRevision]);
 const go=(target,key)=>onNavigate(screenPath(target,key));
 function handleAdmissionImported(result){
  setError('');setNotice(result.notice||`Đã nhập ${result.importedCount} hồ sơ học sinh.`);
  setSearch('');setFilter(current=>({...current,q:'',status:'',page:0}));setRevision(value=>value+1);onChanged?.();
 }
 function paymentUpdated(message){setNotice(message||'Đã cập nhật trạng thái thanh toán.');setRevision(value=>value+1);onChanged?.();}
 async function mutate(values,clearDirty,actionName=null){
  if(pending.current)return false;pending.current=true;setSaving(true);setError('');setNotice('');setFieldErrors({});setFailureCode('');const controller=new AbortController();controllerRef.current=controller;
  try{const response=await api('/api/workspace/screens/'+code+(id?'/'+id:actionName?'/new':'')+(actionName?'/actions/'+actionName:''),{method:'POST',body:JSON.stringify(values),signal:controller.signal});if(!mounted.current)return false;clearDirty?.();onChanged?.();setAction(null);const params=new URLSearchParams(window.location.search);let path=code==='TimeTable_EditSession'&&params.has('returnClassId')&&can('ClassDetail')?screenPath('ClassDetail',values.classId):destination(response);
   if(code==='Attendance_Correction'&&params.get('returnAttendance')==='1')path+='?'+attendanceDetailSearch(params);
   if(path===window.location.pathname){setRevision(current=>current+1);setNotice(response.notice);}else onNavigate(path,{notice:response.notice});return true;
  }catch(cause){if(!mounted.current||controller.signal.aborted)return false;if(cause.status===401)onLogout();else{setError(cause.message);setFailureCode(cause.code||'');setFieldErrors(cause.fields||{});if(cause.code==='TIMETABLE_CONFLICT')setFieldErrors(current=>({...current,startsAt:cause.message}));}return false;}
  finally{pending.current=false;if(mounted.current&&!controller.signal.aborted)setSaving(false);}
 }
 function openAction(key,title,fields=actionForms[key],override={}){setError('');setFieldErrors({});setAction({key,title,fields,override});}
 const run=(key,payload=empty)=>mutate({version:record?.version,...payload},null,key);
 function targetFor(row){if(code==='TimeTable_Conflict')return 'TimeTable_EditSession';if(code==='ListClass_Lecturer')return 'ClassDetail_Lecturer';if(code==='ListEquipmentRequest_IT')return 'EquipmentRequestDetail_IT';if(code==='StudentAssets')return 'StudentAssetReceipt';if(code==='AssetHandover_Receipt')return 'AssetHandover_Receipt';if(code==='TuitionDetail_Student')return 'TuitionDetail_Student';if(code==='PaymentStatus_Admissions')return user.role==='HR'?'PaymentStatus_Admissions':'TrainingContractDetail';if(spec.kind==='timetable'||code==='ClassGradebook')return code;return targets[data?.resource];}
 function openRow(row){const target=targetFor(row);if(target&&can(target))go(target,row.id);}
 function rowActionLabel(row){
  if(code!=='ClassGradebook')return 'Xem chi tiết';
  return data.canWrite&&editableClass(row)?'Nhập điểm':'Xem bảng điểm';
 }
 const genericBack=spec.back||formBacks[code];
 const back=()=>{
  if(code==='ClassGradebook'&&id){go('ClassGradebook');return;}
  if(code==='Attendance_Correction'&&can('ClassAttendance')){
   const params=new URLSearchParams(window.location.search);
   if(params.get('returnAttendance')==='1'){onNavigate(screenPath('ClassAttendance',id)+'?'+attendanceDetailSearch(params));return;}
  }
  if(code==='ClassAttendance'&&can('ListAttendance')){
   const query=attendanceReturnSearch(new URLSearchParams(window.location.search));
   if(query){onNavigate(screenPath('ListAttendance')+query);return;}
  }
  if(code==='AddTrainingContract'&&can('ListStudentAccount')){
   const queue=contractQueueReturnPath(new URLSearchParams(window.location.search));
   if(queue){onNavigate(queue);return;}
  }
  if(user.role==='ADMIN'&&['AddClass','ClassDetail'].includes(code)&&can('CourseSchedules')){onNavigate(screenPath('CourseSchedules')+(record?.courseId?'?'+new URLSearchParams({courseId:record.courseId,year:record.planYear||''}):''));return;}
  if(code==='ListAdmissionApplicationDetail'&&record&&can(admissionHome(record))){const home=admissionHome(record);onNavigate(screenPath(home)+(home==='ListAdmissionEnrollment'?'?tab='+(record.forwardedAt?'forwarded':'waiting'):''));return;}
  const returnClassId=new URLSearchParams(window.location.search).get('returnClassId');
  if(code==='TimeTable_EditSession'&&/^[0-9a-f-]{36}$/i.test(returnClassId||'')&&can('ClassDetail')){go('ClassDetail',returnClassId);return;}
  if(genericBack&&can(genericBack)){
   const backId=['list','notifications','timetable','report'].includes(screens[genericBack]?.kind)||genericBack==='StudentProfileDetail_Student'?null:genericBack==='AssetHandover'?record?.requestId:id;
   if(genericBack!=='AssetHandover'||backId){go(genericBack,backId);return;}
  }
  const fallback={courses:user.role==='LECTURER'?'ListClass_Lecturer':'ListCourse',classes:user.role==='ADMIN'?'CourseSchedules':user.role==='LECTURER'?'ListClass_Lecturer':'ListClass',requests:user.role==='IT_SUPPORT'?'ListEquipmentRequest_IT':'ListEquipmentRequest',loans:user.role==='STUDENT'?'StudentAssets':'ListAssetRecovery',tuition:user.role==='STUDENT'?'TuitionDetail_Student':user.role==='ACCOUNTANT'?'ListTuitionManagement':'PaymentStatus_Admissions',contracts:'ListTrainingContract',students:user.role==='STUDENT'?'StudentProfileDetail_Student':'ListStudent',assets:'ListAsset',incidents:'ListAssetIncident',notifications:'ListNotification',closures:'ListTrainingClosure',payments:'ListPayment'}[data?.resource];
  if(fallback&&can(fallback))go(fallback);else onNavigate('dashboard');
 };
 const openClassSession=session=>onNavigate(screenPath('TimeTable_EditSession',session?.id)+'?classId='+encodeURIComponent(record.id)+'&returnClassId='+encodeURIComponent(record.id));
 const openRelated=(name)=>{const target=name==='contracts'?(user.role==='STUDENT'?'TuitionDetail_Student':'TrainingContractDetail'):name==='transactions'?'PaymentDetail':name==='sessions'?'ClassAttendance':name==='classes'?(user.role==='LECTURER'?'ClassDetail_Lecturer':'ClassDetail'):name==='loans'||(name==='history'&&data?.resource==='assets')?(user.role==='STUDENT'?'StudentAssetReceipt':'AssetHandover_Receipt'):name==='incidents'?'AssetIncidentDetail':null;return target&&can(target)?row=>go(target,row.id):null;};
 const actionButtons=[];
 function addButton(key,label,handler,disabled=false){actionButtons.push(<button key={key} type="button" className="admin-secondary" disabled={saving||disabled} onClick={handler}>{label}</button>);}
 if(record){
  if(code==='ListAdmissionApplicationDetail'&&data.canEditCourse)addButton('update-course',record.enrollmentCourse?'Đổi khóa học':'Chọn khóa học',()=>openAction('update-course','Khóa học đăng ký',[{key:'enrollmentCourse',label:'Khóa học đăng ký',type:'select',source:'enrollmentCourses',required:true,wide:true}]));
  if(code==='ListAdmissionApplicationDetail'&&data.canEditProfile)addButton('edit-profile','Bổ sung / chỉnh sửa hồ sơ',()=>setEditingProfile(true),editingProfile);
  for(const [label,target] of Array.isArray(spec.actions)?spec.actions:[])if(can(target)){let nextId=record.id;if(target==='AddEquipmentRequest'){addButton(target,label,()=>onNavigate(screenPath(target)+'?classId='+record.id));continue;}
   if(target==='AdmissionConsultation'&&(!canRecordConsultation(record.status)||data.canConsult===false))continue;
   if(target==='AddTrainingContract'&&data.resource==='admissions'&&!['PROFILE_PENDING','PROFILE_CREATED'].includes(record.status))continue;
   if(target==='AddTrainingContract'){addButton(target,label,()=>onNavigate(screenPath(target)+'?consultationId='+record.id));continue;}
   if(target==='ClassAttendance')continue; // A session is selected from the actual timetable below.
   if(['list','timetable','notifications','report'].includes(screens[target]?.kind))nextId=null;
   if(data.resource==='students'&&target==='TuitionDetail_Student')nextId=null;
   if(target==='AssetHandover'&&!['READY','PREPARING'].includes(record.status))continue;
   if(target==='EditClass'&&data.resource==='assignments')nextId=record.classId;
   if(target==='EditClass'&&data.resource==='classes'&&['COMPLETED','CANCELLED'].includes(record.status))continue;
   if(target==='TrainingContractDetail'&&data.resource==='students')continue;
   if(target==='ListClass'){addButton(target,label,()=>go(target));continue;}
   if(target==='EditTrainingContract'&&record.status!=='DRAFT')continue;
   if(target==='ContractSignature'&&!canReviewSignedScan(record))continue;
   addButton(target,label,()=>go(target,nextId));
  }
  if(code==='TrainingContractDetail'&&canSendContract(record)&&can('ContractSignature'))addButton('send-for-signature','Gửi tuyển sinh in & ký',()=>openAction('send-for-signature','Gửi hợp đồng cho tuyển sinh ký',[{key:'admissionsId',label:'Nhân viên tuyển sinh nhận hợp đồng',type:'select',source:'admissionsStaff',required:true,wide:true}],{admissionsId:record.defaultAdmissionsId||''}));
  if(code==='TrainingContractDetail'&&canProvisionContract(record)&&!record.studentId&&can('ListStudentAccount'))addButton('create-account','Tạo tài khoản theo email hợp đồng',()=>onNavigate(screenPath('ListStudentAccount')+'?q='+encodeURIComponent(record.studentEmail||'')));
  if(code==='EquipmentRequestDetail_IT'&&data.canWrite){if(record.status==='NEW'){addButton('prepare','Tiếp nhận & chuẩn bị',()=>run('prepare'));addButton('reject','Từ chối yêu cầu',()=>run('reject'));}if(record.status==='PREPARING')addButton('ready','Xác nhận sẵn sàng',()=>run('ready'));}
  if(code==='AutoAssignmentDetail'&&data.canWrite)addButton('confirm','Xác nhận đã kiểm tra',()=>run('confirm'),record.assignmentStatus==='CONFIRMED');
  if(code==='ClassDetail'&&!record.planFixed&&can('TimeTable_EditSession')&&['PLANNED','ACTIVE'].includes(record.status))addButton('add-session','Xếp lịch học cho lớp',()=>openClassSession());
  if(code==='AssetDetail'&&data.canWrite&&related.maintenance?.some(item=>item.status==='OPEN'))addButton('complete-maintenance','Hoàn tất bảo trì',()=>openAction('complete-maintenance','Hoàn tất bảo trì'));
  if(code==='ViewNotification'&&!record.readAt)addButton('read','Đánh dấu đã đọc',()=>run('read'));
  if(code==='ViewNotification'&&record.targetScreen&&can(record.targetScreen))addButton('target',record.targetScreen==='StudentRetake'?'Đăng ký học lại':'Mở nội dung liên quan',()=>go(record.targetScreen,record.targetId));
  if(code==='PaymentDetail'&&data.canWrite&&record.status==='PENDING'){addButton('confirm','Xác nhận thanh toán',()=>openAction('confirm','Đối soát và xác nhận thanh toán'));addButton('mismatch','Ghi nhận sai lệch',()=>openAction('mismatch','Ghi nhận sai lệch'));}
  if(code==='TuitionDetail'&&user.role==='ACCOUNTANT'&&record.status==='SIGNED'&&Number(record.tuitionDue)===0)addButton('activate-zero','Xác nhận hợp đồng miễn học phí',()=>openAction('activate-zero','Xác nhận nghĩa vụ học phí bằng 0',[{key:'changeNote',label:'Căn cứ xác nhận miễn học phí',type:'textarea',required:true}]));
  if(['PaymentDetail','PaymentDetail_Mismatch'].includes(code)&&record.status==='MISMATCH'&&can('PaymentDetail_Mismatch'))addButton('resolve','Ghi nhận đã làm rõ',()=>code==='PaymentDetail_Mismatch'?openAction('resolve','Kết quả làm rõ sai lệch'):go('PaymentDetail_Mismatch',id));
  if(isEquipmentReturn&&data.canWrite&&!record.returnedAt)addButton('recover','Kiểm tra & xác nhận trả',()=>openAction('confirm','Ghi nhận thiết bị đã thực nhận',actionForms.recover));
  if(isEquipmentReturn&&data.canWrite&&record.returnedAt&&refundContext.canRequest){
   addButton('request-refund',refundReview?'Cập nhật phiếu hoàn cọc':'Lập phiếu hoàn cọc',()=>openAction(
    'request-refund','Phiếu đề nghị hoàn cọc gửi kế toán',refundRequestFields,
    refundRequestValues(refundContext.contractVersion,record.studentName,refundReview),
   ));
  }
  if(code==='AssetIncidentDetail'&&data.canWrite&&!record.approvalReference)addButton('incident','Lưu đánh giá kỹ thuật',()=>openAction('save','Đánh giá kỹ thuật thiết bị',[...(!record.loanId?[{key:'loanId',label:'Biên bản bàn giao liên quan',type:'select',source:'loans'}]:[]),...actionForms.incident]));
  if(code==='AssetIncidentDetail'&&record.loanId&&user.role==='IT_SUPPORT'&&can('ListAssetDetail'))addButton('return-detail','Mở hồ sơ trả thiết bị',()=>go('ListAssetDetail',record.loanId));
  if(code==='AssetIncidentDetail'&&can('AssetIncidentDetail_Accountant'))addButton('finance','Mở quyết toán tài chính',()=>go('AssetIncidentDetail_Accountant',id));
  if(code==='AssetIncidentDetail_Accountant'&&data.canWrite&&!record.settledAt)addButton('settle','Ghi nhận quyết toán',()=>openAction('settle','Quyết toán bồi thường'));
 }
 function exportCsv(){const rows=data.items||[],keys=[...new Set(rows.flatMap(Object.keys))].filter(key=>labels[key]);const quote=value=>'"'+String(/^[=+@\-\t\r]/.test(String(value??''))?'\''+value:value??'').replaceAll('"','""')+'"';const lines=[keys.map(key=>quote(labels[key])).join(','),...rows.map(row=>keys.map(key=>quote(display(row[key],key))).join(','))];const link=document.createElement('a'),url=URL.createObjectURL(new Blob(['\ufeff'+lines.join('\r\n')],{type:'text/csv;charset=utf-8'}));link.href=url;link.download=code+'.csv';link.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}
 const consultationClosed=data?.resource==='admissions'&&!canRecordConsultation(record?.status);
 const isDepositRefund=code==='DepositRefund',depositTransfer=isDepositRefund&&Boolean(refundReview);
 const formFields=depositTransfer?refundTransferFields(refundReview):forms[code];
 const showForm=Boolean(formFields&&(record||newForms.has(code))&&data?.canWrite&&!(code==='AdmissionConsultation'&&consultationClosed)&&(!isDepositRefund||refundContext.canSettle&&!depositSettlement));
 const list=data&&!record&&!showForm&&!isReport;
 const saveLabel=isDepositRefund?(depositTransfer&&Number(refundReview.refundAmount)>0?'Xác nhận đã chuyển khoản':'Ghi nhận quyết toán'):(spec.save||'Lưu thông tin');
 const editor=showForm&&<ScreenForm
  key={code+':'+id+(depositTransfer?':'+record.version:'')}
  code={code} fields={formFields} record={record||empty} related={related} options={options}
  user={user} onLogout={onLogout} busy={saving} error={action?'':error} errors={fieldErrors} notice={notice}
  onSave={depositTransfer?(values,clear)=>mutate(refundTransferPayload(values),clear):mutate}
  onBack={back} saveLabel={saveLabel}
  extra={failureCode==='TIMETABLE_CONFLICT'?<TimetableConflictNotice/>:null}
 />;
 return <AdminPage title={screenTitle(code,user)} description={code==='ClassGradebook'&&!id?'Chọn lớp để mở bảng điểm.':spec.description} eyebrow={dashboardRoles[user.role]?.category} code={code}>
  {!showForm&&<AdminMessage error={action?'':error} notice={notice}/>}
  {!id&&listIntro}
  {consultationClosed&&code==='AdmissionConsultation'&&<p className="admin-message">{record?.status==='NOT_PROCEEDING'?'Hồ sơ này đã Không tiếp tục đăng ký và đã kết thúc tư vấn. Khi học viên có nhu cầu trở lại, hãy nhập một hồ sơ đăng ký mới.':'Hồ sơ đã gửi và đã kết thúc tư vấn. Mở chi tiết đăng ký để bổ sung hoặc chỉnh sửa bộ hồ sơ.'}</p>}
  {code==='PaymentDetail_Duplicate'&&record&&<p className="admin-message">Đây là bản ghi giao dịch gốc đã được tiếp nhận. Kiểm tra trạng thái tại đây; không ghi nhận khoản thu thứ hai.</p>}
  {loading?<AdminLoading/>:!data?<AdminLoading error={error} onRetry={()=>setRevision(v=>v+1)}/>:<>
   {code==='StudentRetake'?(record?<StudentRetakeOffer
    record={record} classes={related.classes} canRegister={data.canWrite} canPay={can('TuitionDetail_Student')}
    busy={saving} errors={fieldErrors} onRegister={values=>mutate(values,null,'register')}
    onPay={contractId=>go('TuitionDetail_Student',contractId)} onBack={()=>go('StudentRetake')}
   />:<StudentRetakeList offers={data.items||[]} onOpen={offer=>go('StudentRetake',offer)}/>):isDepositRefund&&record?<>
    <DepositSettlementOverview record={record} context={refundContext} review={refundReview} incidents={related.incidents||[]} settlement={depositSettlement}>
     {['loans','incidents','settlements'].filter(name=>related[name]?.length).map(name=><RelatedTable key={name} rows={related[name]} title={sectionNames[name]} onOpen={openRelated(name)}/>)}
    </DepositSettlementOverview>
    {editor||<button type="button" className="admin-secondary" onClick={back}><ArrowLeft size={14}/>Quay lại</button>}
   </>:showForm?editor
   :isReport?<section className="admin-card screen-report"><div className="screen-toolbar"><label>Từ ngày<input type="date" value={filter.from||data.from} onChange={e=>setFilter(current=>({...current,from:e.target.value}))}/></label><label>Đến ngày<input type="date" value={filter.to||data.to} onChange={e=>setFilter(current=>({...current,to:e.target.value}))}/></label><button type="button" onClick={exportCsv}><Download size={14}/>Xuất CSV</button></div><div className="screen-report-chart">{data.items.map((row,index)=><div key={index}><span>{display(row.name)} · {display(row.status)}</span><meter min="0" max={Math.max(...data.items.map(item=>Number(item.count)),1)} value={Number(row.count)}/><strong>{Number(row.count).toLocaleString('vi-VN')}</strong></div>)}</div><RelatedTable rows={data.items} title="Số liệu trong kỳ"/></section>
   :list&&code==='StudentResults'?<StudentGradeTable data={data} onPage={page=>setFilter(current=>({...current,page}))}/>
   :list?<>
    {code==='ListPayment'&&user.role==='ACCOUNTANT'&&<StudentPaymentReview canReview={can('PaymentDetail')} onLogout={onLogout} onUpdated={paymentUpdated}/>}
    {data.stats?.length===4?<AdminStats labels={data.stats.map(metric=>metric.label)} values={data.stats.map(metric=>metric.value)}/>:<div className="screen-count"><ShieldCheck size={15}/>{data.total} bản ghi trong phạm vi truy cập</div>}
    <section className="admin-card admin-list-card"><header className="admin-card-heading"><div><h2>{spec.title}</h2><p>Tìm kiếm, lọc và mở hồ sơ theo phân công.</p></div><div className="screen-actions">{code==='ListAdmissionApplication'&&data.canImport&&<AdmissionExcelImport disabled={search!==filter.q} onLogout={onLogout} onError={setError} onImported={handleAdmissionImported}/>}{spec.action&&can(spec.action[1])&&<button type="button" onClick={()=>go(spec.action[1])}><Plus size={15}/>{spec.action[0]}</button>}{code==='ListPayment'&&data.canWrite&&<button type="button" onClick={()=>openAction('import','Tiếp nhận chứng từ giao dịch ngân hàng')}><Plus size={15}/>Tiếp nhận giao dịch</button>}{code==='ListAssetIncident'&&data.canWrite&&<button type="button" onClick={()=>openAction('create','Ghi nhận sự cố',[{key:'assetId',label:'Thiết bị',type:'select',source:'assets',required:true},{key:'loanId',label:'Biên bản bàn giao liên quan',type:'select',source:'loans'},...actionForms.incident])}><Plus size={15}/>Ghi nhận sự cố</button>}{code==='ListNotification'&&can('SendNotification')&&<button type="button" onClick={()=>go('SendNotification')}><Plus size={15}/>Gửi thông báo</button>}{code==='TimeTable_Manager'&&can('TimeTable_EditSession')&&<button type="button" onClick={()=>go('TimeTable_EditSession')}><Plus size={15}/>Thêm buổi học</button>}</div></header>
    <div className={"screen-toolbar"+(code==='ListClass'?' class-plan-toolbar':'')}><label className="screen-search"><Search size={16}/><input aria-label="Tìm kiếm hồ sơ" type="search" placeholder="Tìm theo mã, tên hoặc nội dung liên quan…" maxLength={200} value={search} onChange={e=>setSearch(e.target.value)}/></label>{code==='ListClass'&&<select aria-label="Năm kế hoạch" value={filter.year} onChange={event=>setFilter(current=>({...current,year:event.target.value,page:0}))}><option value="">Tất cả năm kế hoạch</option>{(options.planYears||[]).map(year=><option key={year} value={year}>{year}</option>)}</select>}{!['grades','sessions'].includes(data.resource)&&<select aria-label="Lọc trạng thái" value={filter.status} onChange={e=>setFilter(current=>({...current,status:e.target.value,page:0}))}><option value="">Tất cả trạng thái</option>{(filterStates[data.resource]||[]).map(key=><option key={key} value={key}>{statuses[key]}</option>)}</select>}</div>
    {spec.kind==='timetable'?<Timetable rows={data.items} from={filter.from} onWeek={value=>setFilter(current=>({...current,...weekRange(value),page:0}))} onOpen={openRow}/>:<div className="admin-table-scroll"><table className="admin-table"><thead><tr>{(columns[data.resource]||[]).map(key=><th key={key}>{labels[key]}</th>)}<th>Thao tác</th></tr></thead><tbody>{data.items?.map(row=><tr key={row.id}>{(columns[data.resource]||[]).map(key=><td key={key}>{key==='status'&&data.resource==='admissions'?<AdmissionBadges record={row}/>:['status','state','returnStatus'].includes(key)?<Badge value={row[key]}/>:display(row[key],key)}</td>)}<td>{targetFor(row)&&can(targetFor(row))?<button type="button" className="admin-text-button" onClick={()=>openRow(row)}>{rowActionLabel(row)}<ArrowUpRight size={13}/></button>:<span className="admin-muted">Chỉ xem</span>}</td></tr>)}</tbody></table></div>}
    {!data.items?.length&&<div className="admin-empty">Chưa có dữ liệu phù hợp. Các bản ghi sẽ xuất hiện khi được cập nhật vào hệ thống.</div>}
    <AdminPagination page={data.page||0} total={data.total||0} size={25} totalPages={data.totalPages||0} onPage={page=>setFilter(current=>({...current,page}))}/></section>
   </>:record?<>
    <section className="admin-card screen-summary"><div><span>{spec.fe}</span><h2>{record.name||record.fullName||record.title||record.code||record.studentName||spec.title}</h2><p>{record.code||record.classCode||record.contractCode||''}</p></div>{data.resource==='admissions'?<AdmissionBadges record={record}/>:(record.status||record.state||record.returnStatus)&&<Badge value={record.state||record.returnStatus||record.status}/>}</section>
    {code==='TuitionDetail_Student'&&user.role==='STUDENT'&&<StudentContractPayment key={record.id} contractId={record.id} user={user} onLogout={onLogout} onUpdated={paymentUpdated}/>}
    {code==='ClassAttendance'?<Attendance record={record} related={related} editable={data.canWrite} onSave={(values,clear)=>mutate(values,clear,'save')} busy={saving} onCorrection={student=>{const params=attendanceDetailSearch(new URLSearchParams(window.location.search));params.set('studentId',student);onNavigate(screenPath('Attendance_Correction',id)+'?'+params);}} canCorrect={can('Attendance_Correction')}/>
    :code==='ClassGradebook'?<Gradebook record={record} related={related} editable={data.canWrite&&editableClass(record)} busy={saving} onBack={back} onSave={(values,clear,publish)=>mutate(values,clear,publish?'publish':'save')}/>
    :<div className="screen-detail-grid"><section className="admin-card screen-detail"><h2>Thông tin hồ sơ</h2><Facts record={data.resource==='admissions'?{...record,enrollmentCourse:record.enrollmentCourse||'Chưa chọn'}:record} showManager={data.resource!=='classes'} hideEquipmentCatalogs={['assets','requests'].includes(data.resource)}/>{code==='ListAdmissionApplicationDetail'&&<p className="admin-muted">{record.forwardedAt?'Khóa học đăng ký đã chốt khi chuyển hồ sơ sang học vụ.':'Có thể chọn và thay đổi khóa học đăng ký đến khi chuyển hồ sơ sang học vụ.'}</p>}{record.content&&<p className="screen-body">{record.content}</p>}{!editingProfile&&<ProfileDocuments documents={related.documents||[]} legacyEvidence={record.profileEvidence} showCompleteness={Boolean(record.documentStatus)}/>}
     {code==='ListAdmissionApplicationDetail'&&(data.canSubmitProfile||(data.canEditProfile&&editingProfile))&&<AdmissionDocumentUpload key={record.id+':'+editingProfile} record={record} busy={saving} errors={fieldErrors} error={error} onLogout={onLogout} editing={editingProfile} initialDocuments={editingProfile?related.documents||[]:[]} onCancel={()=>setEditingProfile(false)} onSave={(values,clear)=>mutate(values,()=>{clear();setEditingProfile(false);},editingProfile?'update-profile':'submit-profile')}/>}
     {code==='TrainingContractDetail'&&<ContractSigningDetails record={record} onLogout={onLogout} disabled={saving}/>}
     {isEquipmentReturn&&record.returnedAt&&related.refundContext&&user.role==='IT_SUPPORT'&&<RefundReview context={refundContext} review={refundReview} showStatus/>}
     {Object.entries(related).filter(([name,rows])=>Array.isArray(rows)&&!['conditions','unassignedStudents','documents','refundReview'].includes(name)&&!(code==='ClassDetail'&&name==='sessions')&&!(data.resource==='sessions'&&name==='attendance')).map(([name,rows])=><RelatedTable key={name} rows={rows} title={sectionNames[name]||name} onOpen={openRelated(name)}/>)}
     {code==='ClassDetail'&&user.role==='MANAGER'&&data.canWrite&&['PLANNED','ACTIVE'].includes(record.status)&&<ClassEnrollment record={record} students={related.unassignedStudents||[]} busy={saving} error={fieldErrors.students} onSave={(values,clear)=>mutate(values,clear,'add-students')} onReload={()=>{setFieldErrors({});setRevision(current=>current+1);}}/>}
     {code==='ClassDetail'&&<ClassSchedule record={record} sessions={related.sessions||[]} canEdit={can('TimeTable_EditSession')} busy={saving} onAdd={()=>openClassSession()} onEdit={openClassSession}/>}
    </section><aside className="admin-card screen-side-actions"><h2>Thao tác hồ sơ</h2><p className="admin-muted">Các thao tác hiển thị theo quyền và trạng thái hiện tại.</p>{data.canExportContract===true&&<ContractPdfActions key={record.id} record={record} metadata={options.contractTemplate} disabled={saving} onLogout={onLogout} onEdit={record.status==='DRAFT'&&can('EditTrainingContract')?()=>go('EditTrainingContract',record.id):undefined}/>}{actionButtons}{related.conditions&&<ClosureConditions
     record={record}
     conditions={related.conditions}
     showAcademicDetails={user.role==='MANAGER'}
     canWrite={data.canWrite}
     can={can}
     go={go}
     onAcademic={()=>openAction('academic','Xác nhận hoàn thành khóa học')}
     onAcademicFailure={()=>openAction('academic-fail','Xác nhận chưa đạt yêu cầu',actionForms.academic)}
     onClose={()=>{if(window.confirm('Xác nhận đóng hồ sơ khi đã đủ điều kiện?'))run('close');}}
     code={code}
     busy={saving}
    />}<button type="button" className="admin-text-button" onClick={back}><ArrowLeft size={14}/>Quay lại</button></aside></div>}
   </>:null}
  </>}
  {action&&<ActionEditor title={action.title} action={action.key} fields={action.fields} record={{...record,...action.override}} options={options} code={code} busy={saving} errors={fieldErrors} error={error} onSave={(values,clear)=>mutate(action.key==='request-refund'?refundRequestPayload(values):values,clear,action.key)} onClose={()=>{setAction(null);setError('');}}/>}
 </AdminPage>;
}
function Timetable({rows,from,onWeek,onOpen}){const days=Array.from({length:7},(_,i)=>{const day=new Date(from+'T12:00:00');day.setDate(day.getDate()+i);return day;});const shift=offset=>{const next=new Date(from+'T12:00:00');next.setDate(next.getDate()+offset);onWeek(next);};return <><div className="screen-calendar-controls"><button type="button" className="admin-secondary" onClick={()=>shift(-7)}>Tuần trước</button><input type="date" aria-label="Chọn tuần học" value={from} onChange={e=>{if(e.target.value)onWeek(new Date(e.target.value+'T12:00:00'));}}/><button type="button" className="admin-secondary" onClick={()=>shift(7)}>Tuần sau</button></div><div className="admin-table-scroll"><div className="screen-week">{days.map(day=><section key={day.toISOString()}><h3>{day.toLocaleDateString('vi-VN',{weekday:'short',day:'2-digit',month:'2-digit'})}</h3>{(rows||[]).filter(row=>new Date(row.startsAt).toDateString()===day.toDateString()).sort((a,b)=>a.startsAt.localeCompare(b.startsAt)).map(row=><button type="button" key={row.id} onClick={()=>onOpen(row)}><time>{new Date(row.startsAt).toLocaleTimeString('vi-VN',{hour:'2-digit',minute:'2-digit'})} – {new Date(row.endsAt).toLocaleTimeString('vi-VN',{hour:'2-digit',minute:'2-digit'})}</time><strong>{row.title}</strong><span>{row.classCode}</span><span>{row.room} · {row.lecturerName}</span></button>)}</section>)}</div></div></>;}
function Attendance({record,related,editable,busy,onSave,onCorrection,canCorrect}){
 const [rows,setRows]=useState(related.attendance||[]),dirty=useUnsavedChanges();useEffect(()=>{setRows(related.attendance||[]);dirty.current=false;},[related]);const locked=record.attendanceFinalized;
 const save=finalize=>onSave({version:record.version,records:rows.filter(row=>finalize||row.status).map(row=>({studentId:row.studentId,status:row.status||'',note:row.note||''})),finalize},()=>{dirty.current=false;});
 return <section className="admin-card screen-detail"><header className="screen-section-header"><h2>Danh sách điểm danh</h2><span>{locked?'Đã chốt điểm danh':'Chưa chốt'}</span></header><div className="admin-table-scroll"><table className="admin-table"><thead><tr><th>Học viên</th><th>Trạng thái</th><th>Ghi chú</th>{canCorrect&&<th>Điều chỉnh</th>}</tr></thead><tbody>{rows.map((row,index)=><tr key={row.studentId}><td>{row.studentName}</td><td>{editable&&!locked?<select aria-label={'Điểm danh '+row.studentName} value={row.status||''} disabled={busy} onChange={e=>{dirty.current=true;setRows(current=>current.map((r,i)=>i===index?{...r,status:e.target.value}:r));}}><option value="">Chọn trạng thái</option>{['PRESENT','LATE','EXCUSED','ABSENT'].map(status=><option key={status} value={status}>{statuses[status]}</option>)}</select>:display(row.status)}</td><td>{editable&&!locked?<input aria-label={'Ghi chú '+row.studentName} value={row.note||''} disabled={busy} maxLength={1000} onChange={e=>{dirty.current=true;setRows(current=>current.map((r,i)=>i===index?{...r,note:e.target.value}:r));}}/>:row.note||'—'}</td>{canCorrect&&<td><button type="button" className="admin-text-button" disabled={locked||busy} onClick={()=>onCorrection(row.studentId)}>Điều chỉnh</button></td>}</tr>)}</tbody></table></div>{!rows.length&&<p className="admin-muted">Chưa có học viên đang học trong lớp.</p>}{editable&&!locked&&<footer className="screen-actions"><button type="button" className="admin-secondary" disabled={busy||!rows.length} onClick={()=>save(false)}>Lưu tạm</button><button type="button" disabled={busy||!rows.length||rows.some(row=>!row.status)} onClick={()=>save(true)}>Lưu & hoàn tất</button></footer>}</section>;
}
function StudentGradeTable({data,onPage}){
 const grades=data.items||[];
 return <section className="admin-card admin-list-card" aria-label="Điểm đã công bố">
  <div className="admin-card-heading"><p>Điều kiện hoàn thành khóa học là tham dự ít nhất 80% số buổi, có điểm cuối kỳ trên 4 và tổng điểm trên 5.</p></div>
  {grades.length>0?<div className="admin-table-scroll">
   <table className="admin-table">
    <thead><tr><th scope="col">Mã lớp</th><th scope="col">Đầu điểm</th><th scope="col">Điểm (thang 10)</th><th scope="col">Ngày công bố</th></tr></thead>
    <tbody>{grades.map(grade=><tr key={grade.id}>
     <td>{grade.classCode}</td><td>{grade.title}</td><td>{grade.score??'—'}</td><td>{display(grade.publishedAt,'publishedAt')}</td>
    </tr>)}</tbody>
   </table>
  </div>:<div className="admin-empty">Giảng viên chưa công bố điểm cho bạn.</div>}
  <AdminPagination page={data.page||0} total={data.total||0} size={data.size||25} totalPages={data.totalPages||0} onPage={onPage}/>
 </section>;
}

function Gradebook({record,related,editable,busy,onBack,onSave}){
 const [grades,setGrades]=useState(related.grades||[]);
 const dirty=useUnsavedChanges();
 useEffect(()=>{setGrades(related.grades||[]);dirty.current=false;},[related]);

 const assessments=related.assessments||[],students=related.students||[];
 const canEnter=student=>student.status==='ACTIVE'&&!student.closedAt&&!student.academicCompletedAt&&!student.academicFailedAt;
 const scoreKey=(enrollmentId,title)=>enrollmentId+':'+title;
 const scores=new Map(grades.map(grade=>[scoreKey(grade.enrollmentId,grade.title),grade.score]));
 const score=(id,title)=>scores.get(scoreKey(id,title))??'';

 const activeTitles=new Set(assessments.map(assessment=>assessment.title));
 const studentNames=new Map(students.map(student=>[student.enrollmentId,student.studentName]));
 const historical=grades.filter(grade=>grade.score!=null&&!activeTitles.has(grade.title));

 function change(enrollmentId,title,value){
  dirty.current=true;
  setGrades(current=>[...current.filter(grade=>grade.enrollmentId!==enrollmentId||grade.title!==title),{enrollmentId,title,score:value}]);
 }

 function save(publish){
  const records=students.filter(canEnter).flatMap(student=>assessments.map(({title})=>{
   const value=score(student.enrollmentId,title);
   return {enrollmentId:student.enrollmentId,title,score:value===''?null:Number(value)};
  }));
  onSave({version:record.version,records},()=>{dirty.current=false;},publish);
 }

 const disableSave=busy||!assessments.length||!students.some(canEnter);
 return <section className="admin-card screen-detail">
  <header className="screen-section-header">
   <h2>Bảng điểm lớp</h2>
   <button type="button" className="admin-text-button" disabled={busy} onClick={onBack}><ArrowLeft size={14}/>Danh sách lớp</button>
  </header>
  <p className="admin-muted">Thang điểm 10. Chỉ kết quả đã công bố mới hiển thị cho học viên.</p>
  {!assessments.length?<p className="admin-message">Khóa học chưa có đầu điểm.</p>:<div className="admin-table-scroll">
   <table className="admin-table">
    <thead><tr><th scope="col">Học viên</th>{assessments.map(({title,weight})=><th scope="col" key={title}>{title}<small> {weight}%</small></th>)}<th scope="col">Điểm tổng</th></tr></thead>
    <tbody>{students.map(student=>{
     const {enrollmentId,studentName}=student;
     const complete=assessments.every(({title})=>score(enrollmentId,title)!=='');
     const total=complete?assessments.reduce((sum,{title,weight})=>sum+Number(score(enrollmentId,title))*Number(weight)/100,0):null;
     return <tr key={enrollmentId}>
      <td>{studentName}</td>
      {assessments.map(({title})=><td key={title}>{editable&&canEnter(student)
       ?<input type="number" min="0" max="10" step="0.01" aria-label={title+' · '+studentName} value={score(enrollmentId,title)} disabled={busy} onChange={event=>change(enrollmentId,title,event.target.value)}/>
       :score(enrollmentId,title)===''?'—':score(enrollmentId,title)}</td>)}
      <td>{total===null?'Chưa đủ điểm':total.toLocaleString('vi-VN',{minimumFractionDigits:2,maximumFractionDigits:4})}</td>
     </tr>;
    })}</tbody>
   </table>
  </div>}
  {!students.length&&<p className="admin-empty">Lớp chưa có học viên.</p>}
  {editable&&<footer className="screen-actions">
   <button type="button" className="admin-secondary" disabled={disableSave} onClick={()=>save(false)}>Lưu bảng điểm</button>
   <button type="button" disabled={disableSave} onClick={()=>save(true)}>Công bố điểm</button>
  </footer>}
  {historical.length>0&&<section className="screen-related">
   <h3>Điểm theo cấu hình trước</h3>
   <div className="admin-table-scroll"><table className="admin-table">
    <thead><tr><th scope="col">Học viên</th><th scope="col">Đầu điểm</th><th scope="col">Điểm</th><th scope="col">Ngày công bố</th></tr></thead>
    <tbody>{historical.map(grade=><tr key={grade.id}><td>{studentNames.get(grade.enrollmentId)||'—'}</td><td>{grade.title}</td><td>{grade.score}</td><td>{grade.publishedAt?display(grade.publishedAt,'publishedAt'):'Chưa công bố'}</td></tr>)}</tbody>
   </table></div>
  </section>}
 </section>;
}
