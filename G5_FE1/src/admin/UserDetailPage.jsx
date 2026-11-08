import {useRef,useState} from 'react';
import {ArrowLeft,ArrowUpRight,CheckCircle2,Clock3,Pencil,ShieldCheck} from 'lucide-react';
import {api} from '../api';
import useAdminData from './useAdminData';
import {AdminLoading,AdminPage,accountCode,accountStatus,formatTime,roleName} from './AdminUI';
import './user-detail.css';

const tabs=[{key:'account',label:'Thông tin tài khoản'},{key:'role',label:'Vai trò'},{key:'history',label:'Lịch sử thao tác'}];
const knownTime=value=>value?formatTime(value):'Chưa ghi nhận';

function History({items,preview=false}) {
 if(!items.length)return <p className="user-detail-empty">Chưa ghi nhận lịch sử liên quan đến tài khoản này.</p>;
 return <ol className="user-detail-history">{(preview?items.slice(0,3):items).map((item,index)=><li key={item.actionCode+'-'+item.occurredAt+'-'+index}>
  <time dateTime={item.occurredAt}>{formatTime(item.occurredAt)}</time><strong>{item.actionLabel}</strong>
  <span>{item.actorName} · {item.result==='SUCCESS'?'Thành công':'Cần kiểm tra'}</span>{item.changeNote&&<p>{item.changeNote}</p>}
 </li>)}</ol>;
}

export default function UserDetailPage({accountId,onNavigate,onLogout}) {
 const remote=useAdminData(signal=>api('/api/admin/users/'+accountId+'/profile',{signal}),[accountId],onLogout);
 const [tab,setTab]=useState('account'),tabRefs=useRef({}),profile=remote.data,account=profile?.account;
 function selectTab(key,focus=false){setTab(key);if(focus)tabRefs.current[key]?.focus();}
 function tabKeyDown(event,index){
  let next;
  if(event.key==='ArrowRight')next=(index+1)%tabs.length;
  else if(event.key==='ArrowLeft')next=(index+tabs.length-1)%tabs.length;
  else if(event.key==='Home')next=0;
  else if(event.key==='End')next=tabs.length-1;
  if(next!==undefined){event.preventDefault();selectTab(tabs[next].key,true);}
 }
 return <AdminPage title="Chi tiết tài khoản người dùng" description="Xem thông tin và phạm vi quyền của tài khoản được chọn." code="UserDetail">
  {remote.loading||remote.error?<><AdminLoading error={remote.error} onRetry={remote.retry}/><button type="button" className="admin-secondary" onClick={()=>onNavigate('/admin/users')}><ArrowLeft size={14}/>Quay về danh sách</button></>:<>
   <section className="admin-card user-detail-summary" aria-label="Tóm tắt tài khoản"><div><span className="user-detail-eyebrow">TÀI KHOẢN</span><h2>{account.fullName}</h2><p>{accountCode(account.id)} · Cập nhật gần nhất: {knownTime(profile.updatedAt)}</p></div><span className={'admin-badge '+(!account.accountEnabled||account.accountLocked?'is-warning':'')}>{accountStatus(account)}</span></section>
   <div className="user-detail-grid"><section className="admin-card user-detail-profile" aria-labelledby="user-detail-profile-title"><h2 id="user-detail-profile-title">Thông tin hồ sơ</h2>
    <div className="user-detail-tabs" role="tablist" aria-label="Thông tin hồ sơ người dùng">{tabs.map((item,index)=><button key={item.key} ref={element=>{tabRefs.current[item.key]=element;}} id={'user-detail-tab-'+item.key} type="button" role="tab" aria-selected={tab===item.key} aria-controls={'user-detail-panel-'+item.key} tabIndex={tab===item.key?0:-1} onClick={()=>selectTab(item.key)} onKeyDown={event=>tabKeyDown(event,index)}>{item.label}</button>)}</div>
    <div className="user-detail-panel" id="user-detail-panel-account" role="tabpanel" aria-labelledby="user-detail-tab-account" hidden={tab!=='account'}>
     <dl className="user-detail-fields"><div><dt>HỌ VÀ TÊN</dt><dd>{account.fullName}</dd></div><div><dt>EMAIL</dt><dd>{account.email}</dd></div><div><dt>VAI TRÒ</dt><dd>{profile.roleName||roleName(account.role)}</dd></div><div><dt>TRẠNG THÁI</dt><dd>{accountStatus(account)}</dd></div><div><dt>SỐ ĐIỆN THOẠI</dt><dd>{account.phone||'Chưa cập nhật'}</dd></div><div><dt>MÃ TÀI KHOẢN</dt><dd>{accountCode(account.id)}</dd></div><div><dt>NGÀY TẠO</dt><dd>{knownTime(profile.createdAt)}</dd></div><div><dt>ĐĂNG NHẬP GẦN NHẤT</dt><dd>{knownTime(profile.lastLoginAt)}</dd></div></dl>
     <div className="user-detail-note"><ShieldCheck size={17}/><p>Tài khoản chỉ được truy cập các màn được cấp cho vai trò hiện tại và theo phạm vi dữ liệu được phân công.</p></div>
     <h3>Thông tin / lịch sử liên quan</h3><History items={profile.history} preview/>
     {profile.history.length>3&&<button type="button" className="admin-text-button" onClick={()=>selectTab('history',true)}>Xem lịch sử thao tác<ArrowUpRight size={14}/></button>}
    </div>
    <div className="user-detail-panel" id="user-detail-panel-role" role="tabpanel" aria-labelledby="user-detail-tab-role" hidden={tab!=='role'}>
     <dl className="user-detail-fields"><div><dt>VAI TRÒ</dt><dd>{profile.roleName||roleName(account.role)}</dd></div><div><dt>MÃ VAI TRÒ</dt><dd>{account.role}</dd></div></dl><h3>Các màn được cấp quyền</h3>
     {profile.functions.length?<ul className="user-detail-permissions">{profile.functions.map(screen=><li key={screen.code}><CheckCircle2 size={17}/><div><strong>{screen.name}</strong><small>{screen.screenCode} · {screen.code}</small></div></li>)}</ul>:<p className="user-detail-empty">Vai trò hiện tại chưa được cấp quyền truy cập màn nào.</p>}
    </div>
    <div className="user-detail-panel" id="user-detail-panel-history" role="tabpanel" aria-labelledby="user-detail-tab-history" hidden={tab!=='history'}><h3>Hoạt động liên quan gần đây</h3><p className="admin-muted">Các thay đổi hồ sơ và đăng nhập của tài khoản đã được hệ thống ghi nhận.</p><History items={profile.history}/></div>
   </section><aside className="user-detail-aside"><section className="admin-card user-detail-actions"><h2>Thao tác hồ sơ</h2><p>Chỉ chỉnh sửa khi bạn có nhu cầu thay đổi thông tin.</p><button type="button" onClick={()=>onNavigate('/admin/users/'+accountId+'/edit')}><Pencil size={14}/>Chỉnh sửa tài khoản</button><button type="button" className="admin-text-button user-detail-back" onClick={()=>onNavigate('/admin/users')}><ArrowLeft size={13}/>Quay về danh sách</button></section>
    <section className="admin-card user-detail-followup"><h2>Theo dõi xử lý</h2>{tabs.map((item,index)=><button type="button" key={item.key} onClick={()=>selectTab(item.key,true)}><span>{index+1}</span><div><strong>{item.label}</strong><small>{index===0?'Thông tin hiện tại của tài khoản':index===1?'Các màn được cấp theo vai trò':'Những thay đổi đã được ghi nhận'}</small></div></button>)}</section>
    <p className="user-detail-readonly"><Clock3 size={14}/><span>Màn hình chỉ xem thông tin. Để cập nhật, chọn Chỉnh sửa tài khoản.</span></p>
   </aside></div>
  </>}
 </AdminPage>;
}
