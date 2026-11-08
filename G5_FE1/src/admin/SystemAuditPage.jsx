import {useEffect,useState} from 'react';
import {ArrowUpRight,Search} from 'lucide-react';
import {api} from '../api';
import useAdminData from './useAdminData';
import {AdminDialog,AdminLoading,AdminPage,AdminPagination,AdminStats,formatTime,roleName} from './AdminUI';

function LogDetail({id,onClose,onLogout}) {
 const remote=useAdminData(signal=>api('/api/admin/audit-logs/'+id,{signal}),[id],onLogout),entry=remote.data;
 return <AdminDialog title="Chi tiết nhật ký" onClose={onClose}><div className="admin-dialog-body">{remote.loading||remote.error?<AdminLoading error={remote.error} onRetry={remote.retry}/>:<dl className="admin-log-detail"><dt>Hoạt động</dt><dd>{entry.actionLabel}</dd><dt>Đối tượng</dt><dd>{entry.targetId||'Toàn hệ thống'}</dd><dt>Người thực hiện</dt><dd>{entry.actorName} · {roleName(entry.actorRole)}</dd><dt>Thời gian</dt><dd>{formatTime(entry.occurredAt)}</dd><dt>Kết quả</dt><dd>{entry.result==='SUCCESS'?'Thành công':'Cần kiểm tra'} · HTTP {entry.status}</dd><dt>Thao tác</dt><dd>{entry.method} {entry.path}</dd><dt>Ghi chú thay đổi</dt><dd>{entry.changeNote||'Không có ghi chú'}</dd></dl>}<div className="admin-dialog-actions"><button type="button" className="admin-secondary" onClick={onClose} data-initial-focus>Đóng</button></div></div></AdminDialog>;
}
export default function SystemAuditPage({onLogout}) {
 const [search,setSearch]=useState(''),[filter,setFilter]=useState({q:'',action:'',date:'',page:0}),[detail,setDetail]=useState(null);
 useEffect(()=>{const timer=setTimeout(()=>setFilter(current=>current.q===search.trim()?current:{...current,q:search.trim(),page:0}),300);return()=>clearTimeout(timer);},[search]);
 const remote=useAdminData(signal=>api('/api/admin/audit-logs?'+new URLSearchParams({...filter,size:10}),{signal}),[filter],onLogout),data=remote.data;
 return <AdminPage title="Nhật ký hệ thống" description="Tra cứu thay đổi tài khoản, phân quyền, cấu hình và các thao tác nghiệp vụ." code="SystemAuditLog">
  <AdminStats labels={['Sự kiện hôm nay','Thành công hôm nay','Cần kiểm tra hôm nay','Người thao tác hôm nay']} values={data?[data.stats.total,data.stats.success,data.stats.failed,data.stats.actors]:[]}/>
  <section className="admin-card admin-list-card"><header className="admin-card-heading"><div><h2>Nhật ký hệ thống</h2><p>Tìm kiếm, lọc và mở chi tiết từng hoạt động.</p></div></header><div className="admin-toolbar"><label className="admin-search"><Search size={16}/><input type="search" value={search} onChange={e=>setSearch(e.target.value)} maxLength={200} aria-label="Tìm nhật ký" placeholder="Tìm theo tên, mã hoặc nội dung liên quan…"/></label><select aria-label="Loại hoạt động" value={filter.action} onChange={e=>setFilter(current=>({...current,action:e.target.value,page:0}))}><option value="">Tất cả hoạt động</option>{data?.actions.map(action=><option key={action.code} value={action.code}>{action.label}</option>)}</select><input type="date" aria-label="Ngày thao tác" value={filter.date} onChange={e=>setFilter(current=>({...current,date:e.target.value,page:0}))}/></div>
   {remote.loading||remote.error?<AdminLoading error={remote.error} onRetry={remote.retry}/>:<div className="admin-table-scroll"><table className="admin-table"><thead><tr><th>HOẠT ĐỘNG / ĐỐI TƯỢNG</th><th>NGƯỜI THỰC HIỆN</th><th>THỜI GIAN</th><th>KẾT QUẢ</th><th>THAO TÁC</th></tr></thead><tbody>{data.items.map(entry=><tr key={entry.id}><td><strong>{entry.actionLabel}</strong><small>{entry.targetId||'Hệ thống'}</small></td><td>{entry.actorName}</td><td>{formatTime(entry.occurredAt)}</td><td><span className={'admin-badge '+(entry.result==='SUCCESS'?'':'is-warning')}>{entry.result==='SUCCESS'?'Thành công':'Cần kiểm tra'}</span></td><td><button type="button" className="admin-text-button" aria-label={'Xem nhật ký '+entry.actionLabel} onClick={()=>setDetail(entry.id)}>Xem<ArrowUpRight size={15}/></button></td></tr>)}</tbody></table>{!data.items.length&&<div className="admin-empty">Chưa có hoạt động phù hợp với bộ lọc.</div>}</div>}
   {!remote.loading&&!remote.error&&<AdminPagination page={filter.page} total={data?.total||0} totalPages={data?.totalPages||0} onPage={page=>setFilter(current=>({...current,page}))}/>}</section>
  {detail&&<LogDetail id={detail} onClose={()=>setDetail(null)} onLogout={onLogout}/>}
 </AdminPage>;
}
