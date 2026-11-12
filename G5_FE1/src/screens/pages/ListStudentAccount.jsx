import {useEffect,useRef,useState} from 'react';
import {ArrowRight,Mail,RefreshCw,Search,UserPlus} from 'lucide-react';
import {api} from '../../api';
import {AdminDialog,AdminLoading,AdminMessage,AdminPage,AdminPagination,formatTime} from '../../admin/AdminUI';
import {canScreen,screenPath} from '../access';
import StudentExcelImport from '../StudentExcelImport';
import '../student-accounts.css';

const pageSize=10;
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
 const [search,setSearch]=useState(''),[filter,setFilter]=useState({q:'',page:0}),[revision,setRevision]=useState(0);
 const [data,setData]=useState(null),[loading,setLoading]=useState(true),[loadError,setLoadError]=useState('');
 const [selected,setSelected]=useState(null),[creating,setCreating]=useState(false),[createError,setCreateError]=useState(''),[stale,setStale]=useState(false),[notice,setNotice]=useState('');
 const [importError,setImportError]=useState('');
 const mounted=useRef(false),pending=useRef(false),request=useRef(null);
 const refresh=()=>setRevision(value=>value+1);
 const canContract=canScreen('AddTrainingContract',user,functions);

 useEffect(()=>{mounted.current=true;return()=>{mounted.current=false;request.current?.abort();};},[]);
 useEffect(()=>{
  const timer=setTimeout(()=>setFilter(current=>current.q===search.trim()?current:{q:search.trim(),page:0}),300);
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
  setSearch('');setFilter({q:'',page:0});refresh();onChanged?.();
 }
 async function createAccount(event){
  event.preventDefault();
  if(pending.current||!selected?.canCreate||stale)return;
  pending.current=true;setCreating(true);setCreateError('');
  const controller=new AbortController();request.current=controller;
  try{
   const result=await api('/api/workspace/student-accounts/'+encodeURIComponent(selected.id),{
    method:'POST',body:JSON.stringify({expectedEmail:selected.email,version:selected.version}),signal:controller.signal
   });
   if(!mounted.current||controller.signal.aborted)return;
   setSelected(null);setNotice(result.notice||'Đã tạo tài khoản học viên và gửi thông tin đăng nhập đến '+result.email+'.');
   refresh();onChanged?.();
  }catch(cause){
   if(!mounted.current||controller.signal.aborted)return;
   if(cause.status===401)onLogout();
   else {setCreateError(cause.message);setStale(cause.status===409||cause.status===404);}
  }finally{
   pending.current=false;
   if(mounted.current&&!controller.signal.aborted)setCreating(false);
  }
 }

 return <>
  <AdminPage title="Cấp tài khoản học viên" description="Cấp tài khoản từ hồ sơ tuyển sinh đã liên hệ hoặc danh sách học viên nhập trực tiếp bằng Excel." code="ListStudentAccount" eyebrow="QUẢN LÝ HỌC VIÊN / TÀI KHOẢN">
   <div className="student-account-guide"><span className="student-account-guide-icon"><Mail size={22}/></span><div><strong>Dùng email học viên đã cung cấp</strong><p>Hệ thống tự tạo mật khẩu ban đầu và gửi thông tin đăng nhập đến email trong hồ sơ khi cấp tài khoản.</p></div></div>
   <AdminMessage notice={notice} error={importError}/>
   <section className="admin-card admin-list-card student-accounts-list">
    <header className="admin-card-heading"><div><h2>Danh sách học viên</h2><p>Hồ sơ tuyển sinh đã liên hệ và học viên nhập từ Excel. Tải file mẫu để thêm học viên trực tiếp.</p></div><div className="student-account-import-actions"><StudentExcelImport mode="studentAccounts" disabled={creating} onLogout={onLogout} onError={setImportError} onImported={handleImported}/></div></header>
    <div className="student-account-toolbar"><label className="student-account-search"><Search size={16}/><input type="search" aria-label="Tìm học viên theo họ tên, email hoặc số điện thoại" placeholder="Tìm họ tên, email hoặc số điện thoại…" maxLength={200} value={search} onChange={event=>setSearch(event.target.value)}/></label><button type="button" className="admin-secondary" disabled={loading||creating} onClick={refresh}><RefreshCw size={15}/>Tải lại</button></div>
    {loading||loadError?<AdminLoading error={loadError} onRetry={refresh}/>:<>
     <div className="admin-table-scroll"><table className="admin-table student-account-table"><thead><tr><th>HỌC VIÊN</th><th>THÔNG TIN LIÊN HỆ</th><th>KHÓA HỌC QUAN TÂM</th><th>TÀI KHOẢN</th><th>THAO TÁC</th></tr></thead><tbody>
      {(data?.items||[]).map(row=><tr key={row.id}>
       <td><strong>{row.fullName||'Chưa cập nhật họ tên'}</strong><small><span className={'student-account-source'+(row.source==='EXCEL'?' is-excel':'')}>{row.source==='EXCEL'?'Nhập từ Excel':'Tuyển sinh · Đã liên hệ'}</span></small><small>{row.source==='EXCEL'?'Ngày nhập':'Đăng ký'}: {formatTime(row.createdDate)}</small></td>
       <td><span className={!row.email?'student-account-missing':''}>{row.email||'Chưa có email'}</span><small>{row.phone||'Chưa có số điện thoại'}</small><small>{contactTimes[row.contactTime]||row.contactTime||'Chưa có thời gian liên hệ'}</small></td>
       <td>{row.programName||'Chưa cập nhật'}</td>
       <td><span className={'admin-badge'+(['OTHER_ROLE','DISABLED','LOCKED','INVALID_EMAIL'].includes(row.accountStatus)?' is-warning':row.accountStatus==='NONE'?' student-account-uncreated':'')}>{accountLabels[row.accountStatus]||'Cần kiểm tra hồ sơ'}</span>{!row.canCreate&&row.blockedReason&&<small className="student-account-reason">{row.blockedReason}</small>}</td>
       <td><div className="student-account-actions">{row.canCreate?<button type="button" className="admin-secondary" disabled={creating} onClick={()=>openCreate(row)} aria-label={'Tạo tài khoản cho '+row.fullName}><UserPlus size={15}/>Tạo tài khoản</button>:row.accountStatus==='STUDENT'&&canContract?<button type="button" className="admin-text-button" onClick={()=>onNavigate(screenPath('AddTrainingContract')+'?'+new URLSearchParams({consultationId:row.id}))}>Lập hợp đồng<ArrowRight size={14}/></button>:<span className="admin-muted">{row.accountStatus==='STUDENT'?'Đã có tài khoản':'Chưa thể cấp mới'}</span>}</div></td>
      </tr>)}
     </tbody></table>{!data?.items.length&&<div className="admin-empty"><UserPlus size={25}/><p>{filter.q?'Không tìm thấy học viên phù hợp.':'Chưa có học viên. Nhập danh sách từ Excel hoặc tiếp nhận hồ sơ tuyển sinh đã liên hệ.'}</p>{filter.q&&<button type="button" className="admin-secondary" onClick={()=>setSearch('')}>Xóa tìm kiếm</button>}</div>}</div>
     <AdminPagination page={filter.page} total={data?.total||0} totalPages={data?.totalPages||0} size={pageSize} onPage={page=>setFilter(current=>({...current,page}))}/>
    </>}
   </section>
  </AdminPage>
  {selected&&<AdminDialog title="Tạo tài khoản học viên" busy={creating} onClose={closeCreate}><form className="admin-dialog-body student-account-confirm" onSubmit={createAccount} aria-busy={creating}>
   <AdminMessage error={createError}/>
   <p>Tạo tài khoản cho học viên và gửi mật khẩu ban đầu đến email dưới đây.</p>
   <dl><div><dt>Học viên</dt><dd>{selected.fullName}</dd></div><div><dt>Email đăng nhập và nhận thông báo</dt><dd><Mail size={16}/>{selected.email}</dd></div><div><dt>Vai trò</dt><dd>Học viên</dd></div></dl>
   <p className="admin-muted">Mật khẩu được tạo tự động. Học viên dùng email này để đăng nhập và có thể đổi mật khẩu trong phần Thông tin cá nhân.</p>
   <div className="admin-dialog-actions"><button type="button" className="admin-secondary" disabled={creating} data-initial-focus onClick={closeCreate}>Hủy</button>{stale?<button type="button" onClick={reloadList}><RefreshCw size={15}/>Tải lại danh sách</button>:<button type="submit" disabled={creating}><Mail size={15}/>{creating?'Đang tạo và gửi email…':'Tạo và gửi email'}</button>}</div>
  </form></AdminDialog>}
 </>;
}
