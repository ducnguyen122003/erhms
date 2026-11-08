import {useEffect,useRef,useState} from 'react';
import {Activity,Bell,BookOpen,BriefcaseBusiness,Building2,CalendarDays,ChevronRight,CircleHelp,FileText,LayoutDashboard,LogOut,Menu,RefreshCw,Settings2,ShieldCheck,UserRound,Users,Wallet,Wrench,X} from 'lucide-react';
import {api,logout} from './api';
import {dashboardPath,dashboardRoles,firstAllowedPath,resolveRole,roleError,workspaceRoute} from './roles';
import UserProfile from './UserProfile';
import RecruitmentPage from './RecruitmentPage';
import UserAccounts from './admin/UserAccounts';
import UserEditPage from './admin/UserEditPage';
import UserDetailPage from './admin/UserDetailPage';
import UserCreatePage from './admin/UserCreatePage';
import RolePermissionsPage from './admin/RolePermissionsPage';
import SystemAuditPage from './admin/SystemAuditPage';
import SystemSettingsPage from './admin/SystemSettingsPage';
import AdminDashboard from './dashboards/AdminDashboard';
import LecturerDashboard from './dashboards/LecturerDashboard';
import StudentDashboard from './dashboards/StudentDashboard';
import AccountantDashboard from './dashboards/AccountantDashboard';
import ITSupportDashboard from './dashboards/ITSupportDashboard';
import ManagerDashboard from './dashboards/ManagerDashboard';
import HRDashboard from './dashboards/HRDashboard';
import './workspace.css';
import ScreenRouter from './screens/ScreenRouter';
import {DashboardNavigationContext} from './dashboards/DashboardUI';
import {dashboardLink,screenNavigation,screenRoute} from './screens/access';

const dashboards={ADMIN:AdminDashboard,LECTURER:LecturerDashboard,STUDENT:StudentDashboard,ACCOUNTANT:AccountantDashboard,IT_SUPPORT:ITSupportDashboard,MANAGER:ManagerDashboard,HR:HRDashboard};
const roleIcons={ADMIN:ShieldCheck,LECTURER:BookOpen,STUDENT:CalendarDays,ACCOUNTANT:Wallet,IT_SUPPORT:Wrench,MANAGER:Users,HR:BriefcaseBusiness};
const initials=name=>(name||'').trim().split(/\s+/).slice(-2).map(part=>part[0]).join('').toUpperCase()||'ER';
const administration=[['USER_ACCOUNTS','/admin/users','Tài khoản',Users,['users','user-detail','user-edit','user-create'],'FE-02'],['ROLE_PERMISSIONS','/admin/permissions','Vai trò & phân quyền',ShieldCheck,['permissions'],'FE-02'],['SYSTEM_CONFIG','/admin/settings','Cấu hình hệ thống',Settings2,['settings'],'FE-02'],['SYSTEM_AUDIT_LOG','/admin/audit-logs','Nhật ký hệ thống',Activity,['audit'],'FE-02']];

export function Brand(){
 const [name,setName]=useState('TRUNG TÂM ĐÀO TẠO');
 useEffect(()=>{let alive=true;const update=()=>api('/api/system/branding').then(data=>{if(alive&&data.centerName)setName(data.centerName);}).catch(()=>{});update();window.addEventListener('erhms-branding-changed',update);return()=>{alive=false;window.removeEventListener('erhms-branding-changed',update);};},[]);
 return <div className="erhms-brand"><span><Building2 size={22}/></span><div><b>BK-VTCMS</b><small title={name}>{name}</small></div></div>;
}

export default function RoleWorkspace({user,onUserUpdate,onLogout}){
 const role=resolveRole(user),config=dashboardRoles[role],Dashboard=dashboards[role],RoleIcon=roleIcons[role]||CircleHelp;
 const [path,setPath]=useState(window.location.pathname.replace(/\/$/,'')||'/'),[functions,setFunctions]=useState(null),[accessError,setAccessError]=useState(''),[accessReload,setAccessReload]=useState(0);
 const [data,setData]=useState(null),[busy,setBusy]=useState(true),[error,setError]=useState(''),[reload,setReload]=useState(0);
 const [menuOpen,setMenuOpen]=useState(false),[tasksOpen,setTasksOpen]=useState(false),[signingOut,setSigningOut]=useState(false),[logoutError,setLogoutError]=useState(''),[pendingPanel,setPendingPanel]=useState(null);
 const [navigationNotice,setNavigationNotice]=useState('');
 const mainRef=useRef(null),notificationRef=useRef(null),section=workspaceRoute(path,user,functions),has=code=>functions?.includes(code),canDashboard=Boolean(has('DASHBOARD'));
 const adminSection=administration.find(item=>item[4].includes(section));
 const businessRoute=screenRoute(path),businessNavigation=screenNavigation(user,functions||[]);
 function navigate(next,{replace=false,notice=''}={}){if(!window.dispatchEvent(new Event('erhms-before-navigate',{cancelable:true})))return;const target=next==='dashboard'?firstAllowedPath(user,functions||[]):next==='profile'?'/profile':next==='recruitment'?'/recruitment':next;window.history[replace?'replaceState':'pushState']({},'',target);setPath(new URL(target,window.location.origin).pathname);setNavigationNotice(notice);setMenuOpen(false);setTasksOpen(false);}
 useEffect(()=>{const listener=()=>{setPath(window.location.pathname.replace(/\/$/,'')||'/');setNavigationNotice('');setMenuOpen(false);setTasksOpen(false);};window.addEventListener('popstate',listener);return()=>window.removeEventListener('popstate',listener);},[]);
 useEffect(()=>{if(section==='redirect'&&functions)navigate(firstAllowedPath(user,functions),{replace:true});},[section,role,functions]);
 useEffect(()=>{
  if(!config){setFunctions([]);return;}const controller=new AbortController();setAccessError('');
  api('/api/auth/permissions',{signal:controller.signal}).then(result=>{if(!controller.signal.aborted)setFunctions(result.functions);}).catch(cause=>{if(!controller.signal.aborted){if(cause.status===401)onLogout();else setAccessError(cause.message);}});
  return()=>controller.abort();
 },[role,path,accessReload]);
 useEffect(()=>{
  if(!config||!canDashboard){setData(null);setBusy(false);return;}const controller=new AbortController();setBusy(true);setError('');
  api('/api/dashboard/'+config.slug,{signal:controller.signal}).then(result=>{if(!controller.signal.aborted)setData(result);}).catch(cause=>{if(!controller.signal.aborted){if(cause.status===401)onLogout();else setError(cause.message);}}).finally(()=>{if(!controller.signal.aborted)setBusy(false);});return()=>controller.abort();
 },[role,reload,section,canDashboard]);
 useEffect(()=>{const refresh=()=>{if(document.visibilityState==='visible'){setAccessReload(value=>value+1);setReload(value=>value+1);}};window.addEventListener('focus',refresh);document.addEventListener('visibilitychange',refresh);return()=>{window.removeEventListener('focus',refresh);document.removeEventListener('visibilitychange',refresh);};},[]);
 useEffect(()=>{mainRef.current?.focus({preventScroll:true});},[section,path]);
 useEffect(()=>{if(!tasksOpen)return;const close=event=>{if(event.key==='Escape'||(event.type==='pointerdown'&&!notificationRef.current?.contains(event.target)))setTasksOpen(false);};document.addEventListener('keydown',close);document.addEventListener('pointerdown',close);return()=>{document.removeEventListener('keydown',close);document.removeEventListener('pointerdown',close);};},[tasksOpen]);
 useEffect(()=>{if(section!=='dashboard'||!pendingPanel||busy||!data)return;const panel=document.getElementById('dash-panel-'+pendingPanel);panel?.scrollIntoView({behavior:window.matchMedia('(prefers-reduced-motion: reduce)').matches?'instant':'smooth',block:'center'});panel?.focus({preventScroll:true});setPendingPanel(null);},[section,pendingPanel,busy,data]);
 const changed=()=>setReload(value=>value+1);
 async function signout(){if(signingOut)return;setSigningOut(true);setLogoutError('');try{await logout();onLogout();}catch(cause){if(cause.status===401)onLogout();else setLogoutError(cause.message);}finally{setSigningOut(false);}}
 const panels=data?.panels||[],tasks=data?.tasks||[],taskCount=tasks.reduce((sum,item)=>sum+(Number(item.count)||0),0);
 const pageProps={user,onLogout,onNavigate:navigate,onUserUpdate,onChanged:changed,initialNotice:navigationNotice,functions:functions||[]};
 let content;
 if(!functions||accessError)content=<section className="erhms-state" role={accessError?'alert':'status'}>{accessError?<><p>{accessError}</p><button onClick={()=>setAccessReload(value=>value+1)}>Thử lại</button></>:<><RefreshCw size={25} className="erhms-spinning"/><p>Đang tải quyền truy cập…</p></>}</section>;
 else if(section==='users')content=<UserAccounts {...pageProps}/>;
 else if(section==='screen')content=<ScreenRouter key={path} screen={businessRoute.screen} id={businessRoute.id} {...pageProps}/>;
 else if(section==='user-detail')content=<UserDetailPage key={path} {...pageProps} accountId={path.split('/')[3]}/>;
 else if(section==='user-edit')content=<UserEditPage key={path} {...pageProps} accountId={path.split('/')[3]}/>;
 else if(section==='user-create')content=<UserCreatePage {...pageProps}/>;
 else if(section==='permissions')content=<RolePermissionsPage onLogout={onLogout} onChanged={()=>{changed();setAccessReload(value=>value+1);}}/>;
 else if(section==='audit')content=<SystemAuditPage onLogout={onLogout}/>;
 else if(section==='settings')content=<SystemSettingsPage {...pageProps}/>;
 else if(section==='profile')content=<UserProfile user={user} onUserUpdate={onUserUpdate} onLogout={onLogout} onBack={()=>navigate('dashboard')} onChangePassword={()=>navigate('/screens/ChangePassword')}/>;
 else if(section==='recruitment')content=<RecruitmentPage user={user} onLogout={onLogout}/>;
 else if(['forbidden','unsupported','missing'].includes(section))content=<section className="erhms-state" role="alert"><RoleIcon size={36}/><h1>{section==='unsupported'?'Vai trò không hợp lệ':section==='missing'?'Không tìm thấy trang':'Bạn chưa được cấp quyền truy cập màn này'}</h1><p>{section==='unsupported'?roleError(user):'Vui lòng liên hệ quản trị viên để kiểm tra quyền truy cập.'}</p>{functions.length>0&&<button onClick={()=>navigate('dashboard',{replace:true})}>Về màn được cấp quyền</button>}</section>;
 else if(busy)content=<section className="erhms-state" role="status"><RefreshCw size={26} className="erhms-spinning"/><p>Đang tải dashboard…</p></section>;
 else if(error)content=<section className="erhms-state"><CircleHelp size={34}/><h1>Chưa tải được dashboard</h1><p role="alert">{error}</p><button onClick={changed}>Thử lại</button></section>;
 else if(data&&Dashboard)content=<><DashboardNavigationContext.Provider value={detail=>{const link=dashboardLink(user,detail,functions);if(!link)return false;navigate(link);return true;}}><Dashboard data={data} user={user} onNavigate={navigate}/></DashboardNavigationContext.Provider><div className="erhms-refresh"><span><RoleIcon size={13}/>{config.category}</span><button onClick={changed}><RefreshCw size={13}/>Cập nhật dữ liệu</button></div></>;
 return <div className={'erhms-workspace'+(menuOpen?' erhms-menu-open':'')}><a className="erhms-skip" href="#workspace-main">Đến nội dung chính</a>{menuOpen&&<button className="erhms-backdrop" onClick={()=>setMenuOpen(false)} aria-label="Đóng menu"/>}
  <aside className="erhms-sidebar" aria-label="Điều hướng chính"><div className="erhms-brand-row"><Brand/><button className="erhms-mobile-close icon-btn" onClick={()=>setMenuOpen(false)} aria-label="Đóng menu"><X size={19}/></button></div><p className="erhms-nav-label">KHÔNG GIAN LÀM VIỆC</p><nav>
   {has('DASHBOARD')&&<button className={section==='dashboard'?'active':''} aria-current={section==='dashboard'?'page':undefined} onClick={()=>navigate(dashboardPath(user))}><LayoutDashboard size={17}/>Dashboard</button>}
   {administration.filter(item=>has(item[0])).map(([code,target,label,Icon,sections])=><button key={code} className={sections.includes(section)?'active':''} aria-current={sections.includes(section)?'page':undefined} onClick={()=>navigate(target)}><Icon size={17}/>{label}</button>)}
   {businessNavigation.map(item=><button key={item.code} className={businessRoute?.screen.fe===item.fe&&(item.fe==='FE-09'?businessRoute.screen.code.startsWith('AutoAssignment')===item.code.startsWith('AutoAssignment'):item.fe==='FE-15'?businessRoute.screen.code.startsWith('Deposit')===item.code.startsWith('Deposit'):true)?'active':''} onClick={()=>navigate(item.path)}><FileText size={17}/><span>{item.label}</span></button>)}
  </nav><p className="erhms-nav-label erhms-personal-label">CÁ NHÂN</p><nav>{has('PROFILE')&&<button className={section==='profile'?'active':''} onClick={()=>navigate('/profile')}><UserRound size={17}/>Thông tin tài khoản</button>}</nav>
   <div className="erhms-sidebar-bottom"><div className="erhms-sidebar-note"><b><i/>Một hành trình học nghề</b><p>Kết nối tuyển sinh, đào tạo<br/>và quản lý thiết bị.</p></div><div className="erhms-account"><button className="erhms-account-user" disabled={!has('PROFILE')} onClick={()=>navigate('/profile')}><span className="erhms-avatar">{initials(user.fullName)}</span><span><b>{user.fullName}</b><small>{config?.label||'Vai trò không hợp lệ'}</small></span></button><button className="icon-btn" onClick={signout} disabled={signingOut} aria-label="Đăng xuất"><LogOut size={17}/></button></div></div>
  </aside><div className="erhms-main-wrap"><header className="erhms-topbar"><button className="erhms-mobile-toggle icon-btn" onClick={()=>setMenuOpen(value=>!value)} aria-expanded={menuOpen} aria-label="Mở menu"><Menu size={21}/></button><div className="erhms-breadcrumb">Không gian làm việc<ChevronRight size={12}/><b>{adminSection?'Admin':businessRoute?businessRoute.screen.title:section==='profile'?'Thông tin tài khoản':section==='recruitment'?'Tuyển dụng':'Dashboard'}</b></div><span className="erhms-screen-code">{businessRoute?.screen.fe||adminSection?.[5]|| (section==='profile'?'FE-06':'FE-01')}</span><div className="erhms-topbar-actions"><time className="erhms-date">{new Intl.DateTimeFormat('vi-VN',{weekday:'long',day:'numeric',month:'long',year:'numeric',timeZone:'Asia/Ho_Chi_Minh'}).format(new Date())}</time><div className="erhms-notification" ref={notificationRef}><button className="icon-btn" aria-label={'Việc cần xử lý: '+taskCount} aria-expanded={tasksOpen} onClick={()=>setTasksOpen(value=>!value)}><Bell size={18}/>{taskCount>0&&<i/>}</button>{tasksOpen&&<div className="erhms-task-popover"><b>Việc cần chú ý</b>{tasks.length?tasks.map(task=><div key={task.key} className="erhms-task-preview"><span>{task.title}</span><strong>{task.count.toLocaleString('vi-VN')}</strong></div>):<p>Chưa có công việc cần xử lý.</p>}<small>Cập nhật cùng dữ liệu dashboard.</small></div>}</div><button className="erhms-avatar" onClick={()=>has('PROFILE')&&navigate('/profile')} aria-label="Mở thông tin tài khoản">{initials(user.fullName)}</button></div></header>
   <main id="workspace-main" className="erhms-main" ref={mainRef} tabIndex={-1}>{logoutError&&<div role="alert" className="error">{logoutError}</div>}{content}</main><footer className="erhms-footer"><span><ShieldCheck size={13}/>BK-VTCMS · Kết nối tuyển sinh, đào tạo và quản lý thiết bị.</span><span>{data?.updatedAt?'Cập nhật: '+new Date(data.updatedAt).toLocaleString('vi-VN',{timeZone:'Asia/Ho_Chi_Minh'}):''}</span></footer>
  </div></div>;
}
