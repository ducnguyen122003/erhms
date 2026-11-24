import {useEffect,useRef,useState} from 'react';
import {ArrowRight,Mail,RefreshCw,Search,UserPlus} from 'lucide-react';
import {api} from '../../api';
import {AdminDialog,AdminLoading,AdminMessage,AdminPage,AdminPagination,formatTime} from '../../admin/AdminUI';
import {canScreen,screenPath} from '../access';
import {display} from '../definitions';
import ProfileDocuments from '../ProfileDocuments';
import StudentExcelImport from '../StudentExcelImport';
import '../student-accounts.css';

const pageSize=10;
const batchLimit=20;
const contractStage='NEEDS_CONTRACT';
const accountLabels={
 NONE:'Chưa có tài khoản',
 STUDENT:'Đã có tài khoản học viên',
 OTHER_ROLE:'Email đã được sử dụng',
 DISABLED:'Tài khoản bị vô hiệu hóa',
 LOCKED:'Tài khoản bị khóa',
 INVALID_EMAIL:'Cần kiểm tra email'
};
const contactTimes={any:'Thời gian phù hợp',morning:'Buổi sáng',afternoon:'Buổi chiều'};

export default function ListStudentAccount({user,functions=[],onLogout,onChanged,onNavigate}){
 const [search,setSearch]=useState(()=>new URLSearchParams(window.location.search).get('q')||'');
 const [filter,setFilter]=useState(()=>({q:search.trim(),page:0,stage:new URLSearchParams(window.location.search).get('stage')===contractStage?contractStage:''}));
 const [revision,setRevision]=useState(0);
 const [data,setData]=useState(null),[loading,setLoading]=useState(true),[loadError,setLoadError]=useState('');
 const [selected,setSelected]=useState(null),[creating,setCreating]=useState(false),[createError,setCreateError]=useState(''),[stale,setStale]=useState(false),[notice,setNotice]=useState('');
 const [batchSelection,setBatchSelection]=useState(()=>new Map());
 const [batchOpen,setBatchOpen]=useState(false),[batchFailures,setBatchFailures]=useState([]);
 const [importError,setImportError]=useState('');
 const mounted=useRef(false),pending=useRef(false),request=useRef(null);
 const refresh=()=>setRevision(value=>value+1);
 const canContract=canScreen('AddTrainingContract',user,functions);
 const needsContract=filter.stage===contractStage;

 useEffect(()=>{mounted.current=true;return()=>{mounted.current=false;request.current?.abort();};},[]);
 useEffect(()=>{
  const timer=setTimeout(()=>setFilter(current=>current.q===search.trim()?current:{...current,q:search.trim(),page:0}),300);
  return()=>clearTimeout(timer);
 },[search]);
 useEffect(()=>{
  const controller=new AbortController();setLoading(true);setLoadError('');
  const params=new URLSearchParams({...filter,size:pageSize});
  api('/api/workspace/student-accounts?'+params,{signal:controller.signal})
   .then(result=>{
    if(controller.signal.aborted)return;
    if(filter.page>0&&filter.page>=result.totalPages){setFilter(current=>({...current,page:Math.max(0,result.totalPages-1)}));return;}
    setData(result);
    setBatchSelection(current=>{
     const staleRows=result.items.filter(row=>{
      const chosen=current.get(row.id);
      return chosen&&(!row.canCreate||row.email!==chosen.email||row.version!==chosen.version);
     });
     if(!staleRows.length)return current;
     const next=new Map(current);
     for(const row of staleRows)next.delete(row.id);
     return next;
    });
   })
   .catch(cause=>{if(!controller.signal.aborted){if(cause.status===401)onLogout();else setLoadError(cause.message);}})
   .finally(()=>{if(!controller.signal.aborted)setLoading(false);});
  return()=>controller.abort();
 },[filter,revision]);

 function openCreate(row){setCreateError('');setStale(false);setNotice('');setSelected(row);}
 function closeCreate(){if(!pending.current)setSelected(null);}
 function reloadList(){setSelected(null);setCreateError('');setStale(false);refresh();}
 function handleImported(result){
  setImportError('');setNotice(result.notice||`Đã nhập ${result.importedCount} học viên từ Excel.`);
  setSearch('');setFilter(current=>({...current,q:'',page:0}));refresh();onChanged?.();
 }
 function selectStage(stage){
  if(stage===filter.stage)return;
  setLoading(true);setFilter(current=>({...current,stage,page:0}));
 }
 function openContract(row){
  const params=new URLSearchParams({consultationId:row.id});
  if(needsContract){
   params.set('returnStage',contractStage);
   if(filter.q)params.set('returnQuery',filter.q);
  }
  onNavigate(screenPath('AddTrainingContract')+'?'+params);
 }
 const rows=data?.items||[];
 const eligibleRows=rows.filter(row=>row.canCreate);
 const selectedOnPage=eligibleRows.filter(row=>batchSelection.has(row.id)).length;
 const allSelectedOnPage=eligibleRows.length>0&&selectedOnPage===eligibleRows.length;

 function updateBatchSelection(rowsToChange,checked){
  setBatchSelection(current=>{
   const next=new Map(current);
   for(const row of rowsToChange){
    if(checked&&row.canCreate&&next.size<batchLimit)next.set(row.id,row);
    if(!checked)next.delete(row.id);
   }
   return next;
  });
 }

 function openBatch(){
  setCreateError('');setBatchFailures([]);setNotice('');setBatchOpen(true);
 }

 async function submitAccounts(endpoint,input,onSuccess,onError){
  if(pending.current)return;
  pending.current=true;setCreating(true);setCreateError('');
  const controller=new AbortController();request.current=controller;
  try{
   const result=await api('/api/workspace/student-accounts/'+endpoint,{
    method:'POST',body:JSON.stringify(input),signal:controller.signal
   });
   if(!mounted.current||controller.signal.aborted)return;
   onSuccess(result);
   refresh();onChanged?.();
  }catch(cause){
   if(!mounted.current||controller.signal.aborted)return;
   if(cause.status===401)onLogout();
   else {setCreateError(cause.message);onError?.(cause);}
  }finally{
   pending.current=false;
   if(mounted.current&&!controller.signal.aborted)setCreating(false);
  }
 }

 async function createAccount(event){
  event.preventDefault();
  if(!selected?.canCreate||stale)return;
  await submitAccounts(encodeURIComponent(selected.id),{expectedEmail:selected.email,version:selected.version},result=>{
   updateBatchSelection([selected],false);
   setBatchFailures(current=>current.filter(row=>row.id!==selected.id));
   setSelected(null);setNotice(result.notice||'Đã xử lý tài khoản cho '+result.email+'.');
  },cause=>setStale(cause.status===409||cause.status===404));
 }

 async function createSelectedAccounts(event){
  event.preventDefault();
  if(!batchSelection.size)return;
  const students=Array.from(batchSelection.values(),({id,email,version})=>({id,expectedEmail:email,version}));
  await submitAccounts('batch',{students},result=>{
   const failures=result.results.filter(row=>row.status==='FAILED').map(row=>({
    ...row,student:batchSelection.get(row.id)
   }));
   setBatchFailures(failures);
   updateBatchSelection(result.results.filter(row=>row.status!=='FAILED'),false);
   setBatchOpen(false);setNotice(result.createdCount+result.linkedCount>0?result.notice:'');
  });
 }

 return <>
  <AdminPage title="Cấp tài khoản học viên" description="Chỉ cấp tài khoản sau khi học viên ký hợp đồng và QLHV xác nhận bản scan đã ký." code="ListStudentAccount" eyebrow="QUẢN LÝ HỌC VIÊN / TÀI KHOẢN">
   <div className="student-account-guide"><span className="student-account-guide-icon"><Mail size={22}/></span><div><strong>Dùng email học viên đã cung cấp</strong><p>Hệ thống tự tạo mật khẩu ban đầu và gửi thông tin đăng nhập đến email trong hồ sơ khi cấp tài khoản.</p></div></div>
   <AdminMessage notice={notice} error={importError}/>
   {batchFailures.length>0&&<section className="admin-card student-account-batch-failures" aria-labelledby="batch-failures-heading">
    <h2 id="batch-failures-heading">Hồ sơ chưa xử lý ({batchFailures.length})</h2>
    <ul>{batchFailures.map(({id,student,message})=><li key={id}><strong>{student?.fullName||student?.email||'Hồ sơ học viên'}</strong>{student?.email&&<span> · {student.email}</span>}<p>{message||'Không xử lý được hồ sơ. Hãy tải lại danh sách và kiểm tra.'}</p></li>)}</ul>
   </section>}
   <section className="admin-card admin-list-card student-accounts-list">
    <header className="admin-card-heading"><div><h2>{needsContract?'Danh sách cần tạo hợp đồng':'Hồ sơ chưa hoàn tất cấp tài khoản'}</h2><p>{needsContract?'Hồ sơ đã được chuyển sang học vụ hoặc nhập từ Excel và chưa có hợp đồng.':'Lập hợp đồng → tuyển sinh in, lấy chữ ký và gửi bản scan → QLHV xác nhận → cấp tài khoản theo email học viên.'}</p></div><div className="student-account-import-actions"><StudentExcelImport mode="studentAccounts" disabled={creating} onLogout={onLogout} onError={setImportError} onImported={handleImported}/></div></header>
    <div className="student-account-stages" role="group" aria-label="Lọc theo bước xử lý">
     <button type="button" aria-pressed={!needsContract} disabled={creating} onClick={()=>selectStage('')}>Chưa cấp tài khoản</button>
     <button type="button" aria-pressed={needsContract} disabled={creating} onClick={()=>selectStage(contractStage)}>Cần tạo hợp đồng{!loading&&!loadError&&typeof data?.needsContractCount==='number'&&<span className="student-account-stage-count">{data.needsContractCount}</span>}</button>
    </div>
    <div className="student-account-toolbar"><label className="student-account-search"><Search size={16}/><input type="search" aria-label="Tìm học viên theo họ tên, email hoặc số điện thoại" placeholder="Tìm họ tên, email hoặc số điện thoại…" maxLength={200} value={search} onChange={event=>setSearch(event.target.value)}/></label><button type="button" className="admin-secondary" disabled={loading||creating} onClick={refresh}><RefreshCw size={15}/>Tải lại</button></div>
    {!needsContract&&<div className="student-account-batch-toolbar">
     <span role="status">Đã chọn <strong>{batchSelection.size}</strong>/{batchLimit} hồ sơ</span>
     {batchSelection.size>0&&<button type="button" className="admin-text-button" disabled={creating} onClick={()=>setBatchSelection(new Map())}>Bỏ chọn tất cả</button>}
     <button type="button" disabled={!batchSelection.size||loading||creating} onClick={openBatch}><UserPlus size={15}/>Tạo tài khoản đã chọn</button>
    </div>}
    {loading||loadError?<AdminLoading error={loadError} onRetry={refresh}/>:<>
     <div className="admin-table-scroll"><table className={'admin-table student-account-table'+(needsContract?' is-contract-queue':'')}><thead><tr>
      {!needsContract&&<th className="student-account-select" scope="col"><input type="checkbox" aria-label="Chọn tất cả hồ sơ đủ điều kiện trên trang này"
       ref={input=>{if(input)input.indeterminate=selectedOnPage>0&&!allSelectedOnPage;}}
       checked={allSelectedOnPage} disabled={creating||!eligibleRows.length||(!allSelectedOnPage&&batchSelection.size+eligibleRows.length-selectedOnPage>batchLimit)}
       onChange={event=>updateBatchSelection(eligibleRows,event.target.checked)}/></th>}
      <th>HỌC VIÊN</th><th>THÔNG TIN LIÊN HỆ</th><th>KHÓA HỌC ĐĂNG KÝ</th>{!needsContract&&<th>TÀI KHOẢN</th>}<th>THAO TÁC</th></tr></thead><tbody>
      {rows.map(row=><tr key={row.id} className={batchSelection.has(row.id)?'student-account-selected':undefined}>
       {!needsContract&&<td className="student-account-select"><input type="checkbox" aria-label={'Chọn hồ sơ '+(row.fullName||row.email)}
        checked={batchSelection.has(row.id)} disabled={!row.canCreate||creating||(!batchSelection.has(row.id)&&batchSelection.size>=batchLimit)}
        onChange={event=>updateBatchSelection([row],event.target.checked)}/></td>}
       <td><strong>{row.fullName||'Chưa cập nhật họ tên'}</strong>{row.studentCode&&<small>{row.studentCode}</small>}<small><span className={'student-account-source'+(row.source==='EXCEL'?' is-excel':'')}>{row.source==='EXCEL'?'Nhập từ Excel':'Tuyển sinh · '+display(row.profileStatus==='PROFILE_PENDING'&&row.documentStatus?row.documentStatus:row.profileStatus)}</span></small><small>{row.source==='EXCEL'?'Ngày nhập':'Đăng ký'}: {formatTime(row.createdDate)}</small></td>
       <td><span className={!row.email?'student-account-missing':''}>{row.email||'Chưa có email'}</span><small>{row.phone||'Chưa có số điện thoại'}</small><small>{contactTimes[row.contactTime]||row.contactTime||'Chưa có thời gian liên hệ'}</small></td>
       <td>{row.enrollmentCourse||(row.source==='EXCEL'?row.programName:'')||'Chưa cập nhật'}<small>Quan tâm: {row.programName||'Chưa cập nhật'}</small>{row.contractCode&&<small>Hợp đồng: {row.contractCode}</small>}{row.contractSigningStatus&&<small>{display(row.contractSigningStatus)}</small>}{row.documentStatus&&row.profileStatus!=='PROFILE_PENDING'&&<small>{display(row.documentStatus)}</small>}<ProfileDocuments documents={row.profileDocuments||[]} legacyEvidence={row.profileEvidence} compact/></td>
       {!needsContract&&<td><span className={'admin-badge'+(['OTHER_ROLE','DISABLED','LOCKED','INVALID_EMAIL'].includes(row.accountStatus)?' is-warning':row.accountStatus==='NONE'?' student-account-uncreated':'')}>{accountLabels[row.accountStatus]||'Cần kiểm tra hồ sơ'}</span>{!row.canCreate&&row.blockedReason&&<small className="student-account-reason">{row.blockedReason}</small>}</td>}
       <td><div className="student-account-actions">{row.canCreate?<button type="button" className="admin-secondary" disabled={creating} onClick={()=>openCreate(row)} aria-label={'Tạo tài khoản cho '+row.fullName}><UserPlus size={15}/>Tạo tài khoản</button>:row.canContract&&!row.hasContract&&canContract?<button type="button" className="admin-text-button" onClick={()=>openContract(row)}>Lập hợp đồng<ArrowRight size={14}/></button>:<span className="admin-muted">{needsContract?'Cần quyền lập hợp đồng':row.profileStatus==='PROFILE_CREATED'?'Đã tạo tài khoản':'Chưa thể cấp tài khoản'}</span>} {row.contractId&&canScreen('TrainingContractDetail',user,functions)&&<button type="button" className="admin-text-button" onClick={()=>onNavigate(screenPath('TrainingContractDetail',row.contractId))}>Xem hợp đồng<ArrowRight size={14}/></button>}</div></td>
      </tr>)}
     </tbody></table>{!rows.length&&<div className="admin-empty"><UserPlus size={25}/><p>{filter.q?'Không tìm thấy hồ sơ phù hợp.':needsContract?'Hiện không có hồ sơ cần tạo hợp đồng.':'Hiện không có hồ sơ cần cấp tài khoản.'}</p>{filter.q&&<button type="button" className="admin-secondary" onClick={()=>setSearch('')}>Xóa tìm kiếm</button>}</div>}</div>
     <AdminPagination page={filter.page} total={data?.total||0} totalPages={data?.totalPages||0} size={pageSize} onPage={page=>setFilter(current=>({...current,page}))}/>
    </>}
   </section>
  </AdminPage>
  {batchOpen&&<AdminDialog title={'Cấp tài khoản cho '+batchSelection.size+' học viên'} busy={creating} onClose={()=>{if(!pending.current)setBatchOpen(false);}}>
   <form className="admin-dialog-body student-account-confirm" onSubmit={createSelectedAccounts} aria-busy={creating}>
    <AdminMessage error={createError}/>
    <p>Kiểm tra email trước khi xác nhận. Tài khoản mới sẽ nhận mật khẩu ban đầu qua email. Hồ sơ đã có tài khoản đủ điều kiện sẽ được gắn hợp đồng.</p>
    <ul className="student-account-batch-review" aria-label="Hồ sơ đã chọn">
     {Array.from(batchSelection.values(),row=><li key={row.id}><strong>{row.fullName}</strong><span>{row.email}</span></li>)}
    </ul>
    <div className="admin-dialog-actions"><button type="button" className="admin-secondary" disabled={creating} data-initial-focus onClick={()=>setBatchOpen(false)}>Hủy</button><button type="submit" disabled={creating||!batchSelection.size}><UserPlus size={15}/>{creating?'Đang xử lý…':'Xác nhận cấp tài khoản'}</button></div>
   </form>
  </AdminDialog>}
  {selected&&<AdminDialog title="Tạo tài khoản và gắn hợp đồng" busy={creating} onClose={closeCreate}><form className="admin-dialog-body student-account-confirm" onSubmit={createAccount} aria-busy={creating}>
   <AdminMessage error={createError}/>
   <p>{selected.accountStatus==='STUDENT'?'Gắn hợp đồng với tài khoản học viên hiện có theo email.':'Tạo tài khoản theo email trên hợp đồng và gửi mật khẩu ban đầu đến email dưới đây.'}</p>
   <ProfileDocuments documents={selected.profileDocuments||[]} legacyEvidence={selected.profileEvidence}/>
   <dl><div><dt>Học viên</dt><dd>{selected.fullName}</dd></div><div><dt>Email đăng nhập và nhận thông báo</dt><dd><Mail size={16}/>{selected.email}</dd></div><div><dt>Vai trò</dt><dd>Học viên</dd></div></dl>
   <p className="admin-muted">{selected.accountStatus==='STUDENT'?'Giữ nguyên tài khoản và mật khẩu hiện có.':'Mật khẩu được tạo tự động và gửi đến email học viên.'} Hợp đồng sẽ tự gắn với tài khoản có email này; trạng thái tuyển sinh chuyển thành Đã tạo hồ sơ.</p>
   <div className="admin-dialog-actions"><button type="button" className="admin-secondary" disabled={creating} data-initial-focus onClick={closeCreate}>Hủy</button>{stale?<button type="button" onClick={reloadList}><RefreshCw size={15}/>Tải lại danh sách</button>:<button type="submit" disabled={creating}><UserPlus size={15}/>{creating?'Đang tạo tài khoản…':'Tạo tài khoản và gắn hợp đồng'}</button>}</div>
  </form></AdminDialog>}
 </>;
}
