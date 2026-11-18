import {useEffect,useRef,useState} from 'react';
import {ArrowRight,ArrowUpRight,FileCheck2,FileText,RefreshCw,Search,Send,Users} from 'lucide-react';
import {api} from '../api';
import {AdminDialog,AdminLoading,AdminMessage,AdminPage,AdminPagination,AdminStats} from '../admin/AdminUI';
import {canScreen,screenPath,screens} from './access';
import AdmissionExcelImport from './AdmissionExcelImport';
import {admissionStatus} from './admissionDocuments';
import {admissionDashboardCodes,admissionEnrollmentTab,enrollmentLabels} from './admissionDashboard';
import {display} from './definitions';
import './screens.css';
import './admission-dashboard.css';

function Progress({value}){return <span className={'admin-badge '+(['SIGNED','ACCOUNT_CREATED','PAID'].includes(value)?'':'is-warning')}>{enrollmentLabels[value]||display(value)}</span>;}
function IntakeState({record}){const status=record.documentStatus||admissionStatus(record);return <><span className={'admin-badge '+(['NEW','NOT_PROCEEDING','DOCUMENTS_INCOMPLETE'].includes(status)?'is-warning':'')}>{display(status)}</span>{record.documentStatus&&record.status!=='PROFILE_PENDING'&&<small>Tiến trình: {display(record.status)}</small>}{record.documentDueOn&&record.status==='DOCUMENT_APPOINTMENT'&&<small>Hẹn nộp: {display(record.documentDueOn,'documentDueOn')}</small>}</>;}

export default function AdmissionDashboard({code,user,functions=[],onNavigate,onLogout,onChanged,initialNotice=''}){
 const spec=screens[code],enrollment=code==='ListAdmissionEnrollment';
 const [data,setData]=useState(null),[loading,setLoading]=useState(true),[error,setError]=useState(''),[actionError,setActionError]=useState(''),[notice,setNotice]=useState(initialNotice),[search,setSearch]=useState('');
 const [filter,setFilter]=useState(()=>({q:'',status:'',page:0,...(enrollment?{tab:admissionEnrollmentTab(window.location.search)}:{})})),[revision,setRevision]=useState(0);
 const [selected,setSelected]=useState(()=>new Set()),[confirmRows,setConfirmRows]=useState(null),[forwarding,setForwarding]=useState(false);
 const mounted=useRef(false),pending=useRef(false),forwardRequest=useRef(null);
 const waiting=enrollment&&filter.tab==='waiting',tracking=enrollment&&!waiting,can=target=>canScreen(target,user,functions);
 const canForward=waiting&&data?.canForward&&can('AdmissionConsultation');
 const pageRows=data?.items||[],allSelected=pageRows.length>0&&pageRows.every(row=>selected.has(row.id));
 useEffect(()=>{mounted.current=true;return()=>{mounted.current=false;forwardRequest.current?.abort();};},[]);
 useEffect(()=>{const timer=setTimeout(()=>setFilter(current=>current.q===search?current:{...current,q:search,page:0}),300);return()=>clearTimeout(timer);},[search]);
 useEffect(()=>{
  if(!enrollment)return;
  const pop=()=>{const tab=admissionEnrollmentTab(window.location.search);setFilter(current=>current.tab===tab?current:{q:'',status:'',page:0,tab});setSearch('');};
  window.addEventListener('popstate',pop);return()=>window.removeEventListener('popstate',pop);
 },[enrollment]);
 useEffect(()=>{
  const controller=new AbortController();setLoading(true);setError('');setSelected(new Set());
  api('/api/workspace/screens/'+code+'?'+new URLSearchParams(filter),{signal:controller.signal}).then(result=>{if(!controller.signal.aborted)setData(result);}).catch(cause=>{if(!controller.signal.aborted){if(cause.status===401)onLogout();else setError(cause.message);}}).finally(()=>{if(!controller.signal.aborted)setLoading(false);});
  return()=>controller.abort();
 },[code,filter,revision]);
 function changeTab(tab){if(tab===filter.tab||forwarding)return;setSearch('');setActionError('');setFilter({q:'',status:'',page:0,tab});onNavigate(screenPath(code)+'?'+new URLSearchParams({tab}));}
 function toggle(id){setSelected(current=>{const next=new Set(current);if(next.has(id))next.delete(id);else next.add(id);return next;});}
 function imported(result){setNotice(result.notice||`Đã nhập ${result.importedCount} hồ sơ.`);setSearch('');setFilter({q:'',status:'',page:0});setRevision(value=>value+1);onChanged?.();}
 function detail(row){onNavigate(screenPath('ListAdmissionApplicationDetail',row.id)+'?'+new URLSearchParams({from:code,...(enrollment?{tab:filter.tab}:{})}));}
 async function forward(){
  if(pending.current||!confirmRows?.length)return;
  pending.current=true;setForwarding(true);setActionError('');setNotice('');const controller=new AbortController();forwardRequest.current=controller;
  try{
   const result=await api('/api/workspace/screens/ListAdmissionEnrollment/new/actions/forward-profiles',{method:'POST',body:JSON.stringify({admissions:confirmRows.map(row=>({id:row.id,version:row.version}))}),signal:controller.signal});
   if(!mounted.current||controller.signal.aborted)return;
   setNotice(result.notice);setConfirmRows(null);setSelected(new Set());setRevision(value=>value+1);onChanged?.();
  }catch(cause){if(mounted.current&&!controller.signal.aborted){if(cause.status===401)onLogout();else{setActionError(cause.message);setConfirmRows(null);setRevision(value=>value+1);}}}
  finally{pending.current=false;if(mounted.current&&!controller.signal.aborted)setForwarding(false);}
 }
 const pageTitle=waiting?'Hồ sơ chờ chuyển học vụ':tracking?'Hồ sơ đã chuyển học vụ':spec.title;
 return <AdminPage title={spec.title} description={enrollment?'Tuyển sinh duyệt chuyển hồ sơ đã đủ giấy tờ và theo dõi quá trình học vụ xử lý.':spec.description} eyebrow="TUYỂN SINH & TƯ VẤN" code={code}>
  <div className="admission-dashboard-switch" aria-label="Chuyển dashboard tuyển sinh">{admissionDashboardCodes.filter(can).map(target=><button key={target} type="button" className={target===code?'is-active':'admin-secondary'} aria-current={target===code?'page':undefined} onClick={()=>{if(target!==code)onNavigate(screenPath(target));}}>{target==='ListAdmissionApplication'?<Users size={16}/>:<FileCheck2 size={16}/>} {screens[target].title}</button>)}</div>
  {enrollment&&<div className="admission-enrollment-tabs" role="tablist" aria-label="Các trang hoàn tất thủ tục nhập học">{[['waiting','Chờ chuyển học vụ'],['forwarded','Đã chuyển học vụ']].map(([tab,label])=><button key={tab} id={'admission-tab-'+tab} type="button" role="tab" aria-selected={filter.tab===tab} aria-controls="admission-dashboard-panel" disabled={forwarding} onClick={()=>changeTab(tab)}>{label}<span>{data?.tabs?.[tab]??0}</span></button>)}</div>}
  <AdminMessage error={actionError||(!loading&&data?error:'')} notice={notice}/>
  {loading?<AdminLoading/>:!data?<AdminLoading error={error} onRetry={()=>setRevision(value=>value+1)}/>:<>
   <AdminStats labels={data.stats.map(item=>item.label)} values={data.stats.map(item=>item.value)}/>
   <section className="admin-card admin-list-card admission-dashboard-list" id="admission-dashboard-panel" role={enrollment?'tabpanel':undefined} aria-labelledby={enrollment?'admission-tab-'+filter.tab:undefined}>
    <header className="admin-card-heading"><div><h2>{pageTitle}</h2><p>{waiting?'Hồ sơ đã đủ 5 loại giấy tờ và đang chờ tuyển sinh quyết định chuyển.':tracking?'Theo dõi thời điểm chuyển, hợp đồng, tài khoản và học phí.':'Theo dõi tư vấn, ngày hẹn và các giấy tờ cần bổ sung.'}</p></div>
     {canForward&&<button type="button" className="admission-forward-bulk" disabled={!selected.size||forwarding} onClick={()=>setConfirmRows(pageRows.filter(row=>selected.has(row.id)))}><Send size={15}/>Chuyển quản lý học vụ{selected.size>0?` (${selected.size})`:''}</button>}
     {!enrollment&&data.canImport&&<div className="screen-actions"><AdmissionExcelImport disabled={search!==filter.q} onLogout={onLogout} onError={setError} onImported={imported}/></div>}
    </header>
    <div className={'screen-toolbar admission-dashboard-toolbar'+(!data.filters.length?' has-no-filter':'')}><label className="screen-search"><Search size={16}/><input type="search" aria-label="Tìm kiếm hồ sơ" placeholder="Tìm theo họ tên, email, số điện thoại hoặc khóa học…" maxLength={200} value={search} onChange={event=>setSearch(event.target.value)}/></label>{data.filters.length>0&&<select aria-label="Lọc trạng thái" value={filter.status} onChange={event=>setFilter(current=>({...current,status:event.target.value,page:0}))}><option value="">Tất cả trạng thái</option>{data.filters.map(item=><option key={item.value} value={item.value}>{item.label}</option>)}</select>}<button type="button" className="admin-secondary" disabled={forwarding} onClick={()=>{setActionError('');setRevision(value=>value+1);}} aria-label="Cập nhật danh sách"><RefreshCw size={15}/></button></div>
    <div className="admin-table-scroll"><table className={'admin-table admission-dashboard-table'+(tracking?' is-enrollment':waiting?' is-waiting':'')}>
     <thead><tr>{canForward&&<th className="admission-forward-select"><input type="checkbox" aria-label="Chọn tất cả hồ sơ trên trang" checked={allSelected} ref={input=>{if(input)input.indeterminate=selected.size>0&&!allSelected;}} onChange={()=>setSelected(allSelected?new Set():new Set(pageRows.filter(row=>row.canForward).map(row=>row.id)))}/></th>}<th>Họ và tên</th><th>Khóa quan tâm</th>{waiting?<><th>Ngày gửi hồ sơ</th><th>Trạng thái</th></>:tracking?<><th>Ngày chuyển</th><th>Hợp đồng</th><th>Tài khoản</th><th>Học phí</th></>:<><th>Số điện thoại</th><th>Trạng thái</th></>}<th>Thao tác</th></tr></thead>
     <tbody>{pageRows.map(row=><tr key={row.id}>
      {canForward&&<td className="admission-forward-select"><input type="checkbox" aria-label={'Chọn hồ sơ '+row.fullName} disabled={!row.canForward||forwarding} checked={selected.has(row.id)} onChange={()=>toggle(row.id)}/></td>}
      <td><strong>{row.fullName}</strong><small>{row.email||'Chưa có email'}</small>{enrollment&&<small>{row.phone}</small>}{tracking&&row.documentStatus==='DOCUMENTS_INCOMPLETE'&&<small className="admission-forward-missing">Hồ sơ cần bổ sung giấy tờ</small>}</td><td>{row.programName}</td>
      {waiting?<><td>{display(row.profileSubmittedAt,'profileSubmittedAt')}</td><td><span className="admin-badge is-warning">Chờ chuyển học vụ</span><small>Đã hoàn thành hồ sơ</small></td></>:tracking?<><td>{display(row.forwardedAt,'forwardedAt')}<small>{row.forwardedByName||'Người chuyển chưa cập nhật'}</small></td><td><Progress value={row.contractProgress}/>{row.contractCount>0&&<small>{row.contractCount} hợp đồng · {row.unsignedContractCount} chờ ký</small>}</td><td><Progress value={row.accountProgress}/></td><td><Progress value={row.tuitionProgress}/>{row.tuitionRemaining!==null&&<small>Còn phải đóng: {display(row.tuitionRemaining,'tuitionDue')}</small>}{row.handedOverContractCount<row.contractCount&&<small>{row.contractCount-row.handedOverContractCount} hợp đồng chờ bàn giao</small>}{row.handedOverContractCount>0&&can('PaymentStatus_Admissions')&&<button type="button" className="admin-text-button" onClick={()=>onNavigate(screenPath('PaymentStatus_Admissions')+'?'+new URLSearchParams({q:row.email||row.fullName}))}>Xem học phí<ArrowUpRight size={12}/></button>}</td></>:<><td>{row.phone}</td><td><IntakeState record={row}/></td></>}
      <td><div className="admission-row-actions">{canForward&&row.canForward&&<button type="button" className="admin-secondary admission-forward-row" disabled={forwarding} onClick={()=>setConfirmRows([row])}><Send size={13}/>Chuyển học vụ</button>}{can('ListAdmissionApplicationDetail')?<button type="button" className="admin-text-button" onClick={()=>detail(row)}>{enrollment?'Xem hồ sơ':'Xem chi tiết'}<ArrowUpRight size={13}/></button>:<span className="admin-muted">Chỉ xem</span>}</div></td>
     </tr>)}</tbody>
    </table></div>
    {!pageRows.length&&<div className="admin-empty"><FileText size={26}/><p>{waiting?'Chưa có hồ sơ đủ giấy tờ đang chờ chuyển phù hợp.':tracking?'Chưa có hồ sơ đã chuyển học vụ phù hợp.':'Chưa có hồ sơ tư vấn hoặc cần bổ sung phù hợp.'}</p></div>}
    <AdminPagination page={data.page} total={data.total} size={25} totalPages={data.totalPages} onPage={page=>setFilter(current=>({...current,page}))}/>
   </section>
  </>}
  {confirmRows&&<AdminDialog title="Chuyển quản lý học vụ" busy={forwarding} onClose={()=>setConfirmRows(null)}><div className="admin-dialog-body admission-forward-dialog"><p>Chuyển <strong>{confirmRows.length} hồ sơ</strong> đã hoàn thành sang quản lý học vụ để lập hợp đồng và thực hiện các thủ tục tiếp theo.</p><ul>{confirmRows.map(row=><li key={row.id}><strong>{row.fullName}</strong><small>{row.email}</small></li>)}</ul><div className="admin-dialog-actions"><button type="button" className="admin-secondary" disabled={forwarding} onClick={()=>setConfirmRows(null)} data-initial-focus>Hủy</button><button type="button" disabled={forwarding} onClick={forward}><ArrowRight size={15}/>{forwarding?'Đang chuyển…':'Xác nhận chuyển'}</button></div></div></AdminDialog>}
 </AdminPage>;
}
