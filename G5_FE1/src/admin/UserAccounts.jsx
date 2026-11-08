import {useEffect,useState} from 'react';
import {Eye,Pencil,Plus,Search} from 'lucide-react';
import {api} from '../api';
import {AdminPage,AdminStats,AdminLoading,AdminPagination,accountCode,accountStatus,roleName} from './AdminUI';
import './account-search.css';

export default function UserAccounts({onNavigate,onLogout}) {
 const [search,setSearch]=useState(''),[filter,setFilter]=useState({q:'',roleId:'',status:'',page:0});
 const [data,setData]=useState(null),[roles,setRoles]=useState([]),[busy,setBusy]=useState(true),[error,setError]=useState(''),[reload,setReload]=useState(0);
 useEffect(()=>{const timer=setTimeout(()=>setFilter(current=>current.q===search.trim()?current:{...current,q:search.trim(),page:0}),300);return()=>clearTimeout(timer);},[search]);
 useEffect(()=>{
  const controller=new AbortController();setBusy(true);setError('');
  const params=new URLSearchParams({q:filter.q,status:filter.status,page:filter.page,size:10});if(filter.roleId)params.set('roleId',filter.roleId);
  Promise.all([api('/api/admin/users?'+params,{signal:controller.signal}),api('/api/admin/roles',{signal:controller.signal})]).then(([result,list])=>{
   if(controller.signal.aborted)return;
   if(filter.page>0&&filter.page>=result.totalPages){setFilter(current=>({...current,page:Math.max(0,result.totalPages-1)}));return;}
   setData(result);setRoles(list);
  }).catch(cause=>{if(!controller.signal.aborted){if(cause.status===401)onLogout();else setError(cause.message);}}).finally(()=>{if(!controller.signal.aborted)setBusy(false);});
  return()=>controller.abort();
 },[filter,reload]);
 const stats=data?.stats;
 return <AdminPage title="Tài khoản người dùng" description="Quản lý tài khoản của các vai trò trong trung tâm." code="ListUser">
  <AdminStats labels={['Tổng tài khoản','Hoạt động','Vô hiệu hóa','Bị khóa']} values={stats?[stats.total,stats.active,stats.disabled,stats.locked]:[]}/>
  <section className="admin-card admin-list-card admin-accounts-list"><header className="admin-card-heading"><div><h2>Tài khoản người dùng</h2><p>Tìm kiếm, lọc và mở chi tiết từng hồ sơ.</p></div><button type="button" onClick={()=>onNavigate('/admin/users/new')}><Plus size={15}/>Tạo / cấp tài khoản</button></header>
   <div className="admin-toolbar"><label className="admin-search"><Search size={16}/><input type="search" aria-label="Tìm tài khoản" placeholder="Tìm theo mã, tên hoặc nội dung liên quan…" maxLength={200} value={search} onChange={e=>setSearch(e.target.value)}/></label><label className="admin-sr-only" htmlFor="accounts-role">Lọc vai trò</label><select id="accounts-role" value={filter.roleId} onChange={e=>setFilter(current=>({...current,roleId:e.target.value,page:0}))}><option value="">Tất cả vai trò</option>{roles.map(role=><option value={role.id} key={role.id}>{role.name}</option>)}</select><label className="admin-sr-only" htmlFor="accounts-status">Lọc trạng thái</label><select id="accounts-status" value={filter.status} onChange={e=>setFilter(current=>({...current,status:e.target.value,page:0}))}><option value="">Tất cả trạng thái</option><option value="ACTIVE">Hoạt động</option><option value="DISABLED">Vô hiệu hóa</option><option value="LOCKED">Bị khóa</option></select></div>
   {busy||error?<AdminLoading error={error} onRetry={()=>setReload(v=>v+1)}/>:<div className="admin-table-scroll"><table className="admin-table"><thead><tr><th>NGƯỜI DÙNG / MÃ</th><th>EMAIL</th><th>VAI TRÒ</th><th>TRẠNG THÁI</th><th>THAO TÁC</th></tr></thead><tbody>{data?.items.map(account=><tr key={account.id}><td><strong>{account.fullName}</strong><small>{accountCode(account.id)}</small></td><td>{account.email}</td><td>{roles.find(role=>role.id===account.roleId)?.name||roleName(account.role)}</td><td><span className={'admin-badge '+(!account.accountEnabled||account.accountLocked?'is-warning':'')}>{accountStatus(account)}</span></td><td><div className="admin-account-actions"><button type="button" className="admin-text-button" onClick={()=>onNavigate('/admin/users/'+account.id+'/view')} aria-label={'Xem thông tin '+account.fullName}><Eye size={14}/>Xem</button><button type="button" className="admin-text-button" onClick={()=>onNavigate('/admin/users/'+account.id+'/edit')} aria-label={'Chỉnh sửa '+account.fullName}><Pencil size={14}/>Chỉnh sửa</button></div></td></tr>)}</tbody></table>{!data?.items.length&&<div className="admin-empty">Không tìm thấy tài khoản phù hợp.</div>}</div>}
   {!busy&&!error&&<AdminPagination page={filter.page} total={data?.total||0} totalPages={data?.totalPages||0} onPage={page=>setFilter(current=>({...current,page}))}/>}</section>
 </AdminPage>;
}
