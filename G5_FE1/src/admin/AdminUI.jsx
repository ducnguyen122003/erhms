import {useEffect, useRef} from 'react';
import {ArrowLeft, Check, ChevronLeft, ChevronRight, RefreshCw, ShieldCheck, Users, ListChecks, Clock3, LockKeyhole, X} from 'lucide-react';
import {dashboardRoles} from '../roles';
import './admin.css';

export const accountCode = id => 'TK-' + String(id).padStart(4, '0');
export const accountStatus = account => !account.accountEnabled ? (account.accountLocked ? 'Vô hiệu hóa · Bị khóa' : 'Vô hiệu hóa') : account.accountLocked ? 'Bị khóa' : 'Hoạt động';
export const statusValue = account => !account.accountEnabled ? (account.accountLocked ? 'DISABLED_LOCKED' : 'DISABLED') : account.accountLocked ? 'LOCKED' : 'ACTIVE';
export const roleName = role => dashboardRoles[role]?.label || role || 'Chưa xác định';
export const formatTime = value => value ? new Date(value).toLocaleString('vi-VN', {timeZone: 'Asia/Ho_Chi_Minh', hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit', year: 'numeric'}) : 'Chưa cập nhật';
export function AdminPage({title,description,children,code,eyebrow='ADMIN / USER ACCOUNT AND PERSONAL PROFILE MANAGEMENT'}) {
 return <section className="admin-page" aria-labelledby="admin-page-title"><header className="admin-intro"><span>{eyebrow}</span><h1 id="admin-page-title" tabIndex={-1}>{title}</h1><p>{description}</p></header>{children}<div className="admin-page-caption">{code} · BK-VTCMS</div></section>;
}
export function AdminStats({values,labels}) {
 const icons=[Users,ListChecks,Clock3,LockKeyhole];
 return <section className="admin-stats" aria-label="Các chỉ số tổng quan">{labels.map((label,i)=>{const Icon=icons[i];return <article key={label}><div><span>{label}</span><strong>{Number(values?.[i] || 0).toLocaleString('vi-VN')}</strong></div><i><Icon size={18}/></i></article>;})}</section>;
}
export function AdminMessage({error,notice}) {
 return <>{error && <div className="admin-message is-error" role="alert">{error}</div>}{notice && <div className="admin-message" role="status"><Check size={16}/>{notice}</div>}</>;
}
export function AdminLoading({error,onRetry}) {
 return <div className="admin-empty" role={error?'alert':'status'}>{error?<><p>{error}</p><button type="button" onClick={onRetry}><RefreshCw size={15}/>Thử lại</button></>:<><RefreshCw size={23} className="erhms-spinning"/><p>Đang tải dữ liệu…</p></>}</div>;
}
export function AdminPagination({page,total,size=10,totalPages,onPage}) {
 return <footer className="admin-pagination"><span>{total?`Hiển thị ${page*size+1}–${Math.min((page+1)*size,total)} / ${total.toLocaleString('vi-VN')} mục`:'Không có kết quả'} · Dữ liệu theo bộ lọc</span><div><button type="button" className="admin-secondary" disabled={!page} onClick={()=>onPage(page-1)} aria-label="Trang trước"><ChevronLeft size={15}/></button><b>{page+1}</b><button type="button" className="admin-secondary" disabled={page+1>=totalPages} onClick={()=>onPage(page+1)} aria-label="Trang sau"><ChevronRight size={15}/></button></div></footer>;
}
export function AdminAside({user,tips,updatedBy,updatedAt}) {
 return <aside className="admin-aside">
  <section className="admin-card admin-before-save"><h2>Trước khi lưu</h2><ul>{tips.map(tip=><li key={tip}>{tip}</li>)}</ul><div className="admin-trust"><ShieldCheck size={15}/>Thao tác theo quyền được phân công</div></section>
  <section className="admin-card"><h2>Hồ sơ liên quan</h2><dl className="admin-meta"><dt>NGƯỜI THỰC HIỆN</dt><dd>{user.fullName}</dd><dt>VAI TRÒ</dt><dd>{roleName(user.role)}</dd>{updatedBy && <><dt>CẬP NHẬT GẦN NHẤT</dt><dd>{updatedBy}<small>{formatTime(updatedAt)}</small></dd></>}</dl><p className="admin-meta-note">Thông tin được ghi nhận cùng thời điểm và người cập nhật để thuận tiện tra cứu.</p></section>
 </aside>;
}
export function AdminFormFooter({onBack,busy,label='Lưu thay đổi',children}) {
 return <footer className="admin-form-footer"><button type="button" className="admin-secondary" onClick={onBack} disabled={busy}><ArrowLeft size={14}/>Quay lại</button><div>{children}<button type="submit" disabled={busy}><Check size={15}/>{busy?'Đang lưu…':label}</button></div></footer>;
}
export function AdminDialog({title,children,onClose,busy=false}) {
 const ref=useRef(null);
 useEffect(()=>{const previous=document.activeElement,dialog=ref.current;dialog.showModal();dialog.querySelector('[data-initial-focus]')?.focus();return()=>{dialog.close();if(previous?.isConnected)previous.focus();};},[]);
 return <dialog className="admin-dialog" ref={ref} aria-labelledby="admin-dialog-title" onCancel={event=>{event.preventDefault();if(!busy)onClose();}} onClick={event=>{if(event.target===ref.current&&!busy)onClose();}}><header><h2 id="admin-dialog-title">{title}</h2><button type="button" className="icon-btn" disabled={busy} onClick={onClose} aria-label="Đóng"><X size={18}/></button></header>{children}</dialog>;
}
