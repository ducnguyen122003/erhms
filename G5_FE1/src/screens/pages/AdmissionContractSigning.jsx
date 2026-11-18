import {useEffect,useRef,useState} from 'react';
import {ArrowLeft,ArrowRight,RefreshCw,Search,Upload} from 'lucide-react';
import {api} from '../../api';
import {AdminLoading,AdminMessage,AdminPage,AdminPagination} from '../../admin/AdminUI';
import {screenPath} from '../access';
import {display} from '../definitions';
import ContractPdfActions from '../ContractPdfActions';
import ContractSigningDetails from '../ContractSigningDetails';
import {canSubmitSignedScan,validateSignedScan} from '../contractSigning';
import {useUnsavedChanges} from '../ScreenForm';
import '../screens.css';
import '../contract-signing.css';

const code='AdmissionContractSigning',pageSize=25;
const localToday=()=>{const date=new Date();return new Date(date.getTime()-date.getTimezoneOffset()*60000).toISOString().slice(0,10);};

function SignedScanForm({record,onSaved,onLogout}){
 const [file,setFile]=useState(null),[signedOn,setSignedOn]=useState(''),[savedScan,setSavedScan]=useState(null),[busy,setBusy]=useState(false),[error,setError]=useState(''),[errors,setErrors]=useState({}),[stale,setStale]=useState(false);
 const dirty=useUnsavedChanges(),mounted=useRef(false),pending=useRef(false),request=useRef(null);
 useEffect(()=>{mounted.current=true;return()=>{mounted.current=false;request.current?.abort();};},[]);
 async function submit(event){
  event.preventDefault();if(pending.current||stale)return;
  const invalid=validateSignedScan(file);if(invalid){setError(invalid);return;}
  pending.current=true;setBusy(true);setError('');setErrors({});const controller=new AbortController();request.current=controller;
  try{
   let uploaded=savedScan;
   if(!uploaded){const form=new FormData();form.append('file',file);form.append('screen',code);uploaded=await api('/api/workspace/files',{method:'POST',body:form,signal:controller.signal});if(mounted.current)setSavedScan(uploaded);}
   const result=await api('/api/workspace/screens/'+code+'/'+record.id+'/actions/submit-scan',{method:'POST',body:JSON.stringify({version:record.version,signedOn,signedScanId:uploaded.id}),signal:controller.signal});
   if(mounted.current&&!controller.signal.aborted){dirty.current=false;onSaved(result);}
  }catch(cause){if(mounted.current&&!controller.signal.aborted){if(cause.status===401)onLogout();else{setError(cause.message);setErrors(cause.fields||{});setStale(cause.status===409||cause.status===404);}}}
  finally{pending.current=false;if(mounted.current&&!controller.signal.aborted)setBusy(false);}
 }
 return <form className="contract-signing-upload" onSubmit={submit} aria-busy={busy}><h3>Gửi bản scan đã ký cho QLHV</h3><p className="admin-muted">Scan đầy đủ các trang và chữ ký của học viên thành một file PDF, tối đa 10 MB.</p><AdminMessage error={error}/><fieldset disabled={busy||stale}><div className="admin-field-grid">
  <label className="admin-field" htmlFor="signed-contract-on">Ngày học viên ký *<input id="signed-contract-on" type="date" max={localToday()} value={signedOn} required onChange={event=>{dirty.current=true;setSignedOn(event.target.value);}} aria-invalid={Boolean(errors.signedOn)}/>{errors.signedOn&&<small className="field-error">{errors.signedOn}</small>}</label>
  <label className="admin-field screen-field-wide" htmlFor="signed-contract-scan">Bản scan hợp đồng đã ký *<input id="signed-contract-scan" type="file" accept="application/pdf,.pdf" required onChange={event=>{dirty.current=true;setFile(event.target.files?.[0]||null);setSavedScan(null);setError('');}} aria-invalid={Boolean(errors.signedScanId)}/>{file&&<small>{file.name}</small>}{errors.signedScanId&&<small className="field-error">{errors.signedScanId}</small>}</label>
 </div></fieldset><div className="screen-actions">{stale?<button type="button" className="admin-secondary" onClick={()=>{dirty.current=false;onSaved({notice:'Đã tải lại trạng thái hợp đồng.'});}}><RefreshCw size={15}/>Tải lại trạng thái</button>:<button type="submit" disabled={busy||!file||!signedOn}><Upload size={15}/>{busy?'Đang gửi bản scan…':'Gửi QLHV xác nhận'}</button>}</div></form>;
}

function SigningHistory({rows=[]}){
 if(!rows.length)return null;
 const labels={SENT:'Gửi tuyển sinh ký',SUBMITTED:'Gửi bản scan',APPROVED:'QLHV xác nhận bản ký',REJECTED:'Yêu cầu bổ sung'};
 return <section className="contract-signing-history"><h3>Lịch sử ký hợp đồng</h3><ol>{rows.map((row,index)=><li key={row.id||index}><strong>{labels[row.action]||display(row.action||row.status||row.outcome)}</strong>{row.note&&<p>{row.note}</p>}<small>{row.actorName||''}{row.createdAt?' · '+display(row.createdAt,'createdAt'):''}</small></li>)}</ol></section>;
}

export default function AdmissionContractSigning({accountId:id,onNavigate,onLogout,onChanged,initialNotice=''}){
 const [search,setSearch]=useState(''),[filter,setFilter]=useState({q:'',status:'',page:0}),[revision,setRevision]=useState(0),[data,setData]=useState(null),[loading,setLoading]=useState(true),[error,setError]=useState(''),[notice,setNotice]=useState(initialNotice);
 useEffect(()=>{const timer=setTimeout(()=>setFilter(value=>value.q===search.trim()?value:{...value,q:search.trim(),page:0}),300);return()=>clearTimeout(timer);},[search]);
 useEffect(()=>{const controller=new AbortController();setLoading(true);setError('');api('/api/workspace/screens/'+code+(id?'/'+id:'')+'?'+new URLSearchParams(filter),{signal:controller.signal}).then(result=>{if(!controller.signal.aborted)setData(result);}).catch(cause=>{if(!controller.signal.aborted){if(cause.status===401)onLogout();else setError(cause.message);}}).finally(()=>{if(!controller.signal.aborted)setLoading(false);});return()=>controller.abort();},[id,filter,revision]);
 const refresh=()=>setRevision(value=>value+1),record=data?.record;
 const saved=result=>{setNotice(result.notice||'Đã gửi bản scan cho QLHV xác nhận.');refresh();onChanged?.();};
 return <AdminPage title="In & ký hợp đồng" description="In hợp đồng do QLHV gửi, lấy chữ ký học viên và gửi lại bản scan để xác nhận." code={code} eyebrow="TUYỂN SINH / HỢP ĐỒNG"><AdminMessage error={error} notice={notice}/>{loading?<AdminLoading/>:!data?<AdminLoading error={error} onRetry={refresh}/>:record?<>
  <section className="admin-card screen-summary"><div><span>{record.code}</span><h2>{record.studentName||record.applicantName||record.fullName}</h2><p>{record.courseName||record.registeredCourseName}</p></div><span className={'admin-badge'+(['SENT_FOR_SIGNATURE','SIGNATURE_REVIEW'].includes(record.status)?' is-warning':'')}>{display(record.status)}</span></section>
  <div className="screen-detail-grid"><section className="admin-card screen-detail"><h2>Hợp đồng cần ký</h2><dl className="screen-facts">{[['studentEmail','Email học viên'],['applicantPhone','Số điện thoại'],['tuitionDue','Học phí hợp đồng'],['depositDue','Cọc theo hợp đồng'],['managerName','QLHV phụ trách']].filter(([key])=>record[key]!==undefined&&record[key]!==null).map(([key,label])=><div key={key}><dt>{label}</dt><dd>{display(record[key],key)}</dd></div>)}</dl><ContractSigningDetails record={record} onLogout={onLogout}/>{data.canWrite&&canSubmitSignedScan(record)&&<SignedScanForm key={record.id+':'+record.version} record={record} onSaved={saved} onLogout={onLogout}/>}<SigningHistory rows={data.related?.signingHistory}/></section><aside className="admin-card screen-side-actions"><h2>In hợp đồng</h2><p className="admin-muted">Tải bản PDF QLHV đã gửi để in và cho học viên ký. Sau khi ký, tải bản scan lên để QLHV kiểm tra.</p><ContractPdfActions key={record.id} record={record} onLogout={onLogout}/><button type="button" className="admin-secondary" onClick={refresh}><RefreshCw size={15}/>Tải lại</button><button type="button" className="admin-text-button" onClick={()=>onNavigate(screenPath(code))}><ArrowLeft size={14}/>Về danh sách</button></aside></div>
 </>:<section className="admin-card admin-list-card"><header className="admin-card-heading"><div><h2>Hợp đồng được giao</h2><p>Mở hợp đồng để tải bản in và gửi lại bản scan có chữ ký.</p></div><button type="button" className="admin-secondary" onClick={refresh}><RefreshCw size={15}/>Tải lại</button></header><div className="screen-toolbar"><label className="screen-search"><Search size={16}/><input aria-label="Tìm hợp đồng cần ký" type="search" value={search} maxLength={200} placeholder="Tìm mã hợp đồng, họ tên, email…" onChange={event=>setSearch(event.target.value)}/></label><select aria-label="Trạng thái ký hợp đồng" value={filter.status} onChange={event=>setFilter(value=>({...value,status:event.target.value,page:0}))}><option value="">Tất cả trạng thái</option>{['SENT_FOR_SIGNATURE','SIGNATURE_REVIEW','SIGNED','ACTIVE','COMPLETED'].map(status=><option key={status} value={status}>{display(status)}</option>)}</select></div><div className="admin-table-scroll"><table className="admin-table"><thead><tr><th>Hợp đồng</th><th>Học viên</th><th>Khóa học</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>{(data.items||[]).map(row=><tr key={row.id}><td>{row.code}</td><td>{row.studentName||row.applicantName}<small>{row.studentEmail}</small></td><td>{row.courseName||row.registeredCourseName}</td><td><span className={'admin-badge'+(['SENT_FOR_SIGNATURE','SIGNATURE_REVIEW'].includes(row.status)?' is-warning':'')}>{display(row.status)}</span>{row.status==='SENT_FOR_SIGNATURE'&&row.signatureReviewNote&&<small>Cần bổ sung bản ký</small>}</td><td><button type="button" className="admin-text-button" onClick={()=>onNavigate(screenPath(code,row.id))}>Mở hợp đồng<ArrowRight size={14}/></button></td></tr>)}</tbody></table>{!data.items?.length&&<div className="admin-empty">Chưa có hợp đồng được giao phù hợp với bộ lọc.</div>}</div><AdminPagination page={data.page??filter.page} total={data.total||0} size={data.size||pageSize} totalPages={data.totalPages??Math.ceil((data.total||0)/(data.size||pageSize))} onPage={page=>setFilter(value=>({...value,page}))}/></section>}</AdminPage>;
}
