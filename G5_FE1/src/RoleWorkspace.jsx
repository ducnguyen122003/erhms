import {useEffect, useRef, useState} from 'react';
import {Activity, Bell, BookOpen, BriefcaseBusiness, Building2, CalendarDays, ChevronRight, CircleHelp, ClipboardList, FileText, LayoutDashboard, LogOut, Menu, RefreshCw, ShieldCheck, UserRound, Users, Wallet, Wrench, X} from 'lucide-react';
import {api, logout} from './api';
import {dashboardPath, dashboardRoles, resolveRole, roleError, workspaceRoute} from './roles';
import UserProfile from './UserProfile';
import UserAccounts from './admin/UserAccounts';
import RecruitmentPage from './RecruitmentPage';
import AdminDashboard from './dashboards/AdminDashboard';
import LecturerDashboard from './dashboards/LecturerDashboard';
import StudentDashboard from './dashboards/StudentDashboard';
import AccountantDashboard from './dashboards/AccountantDashboard';
import ITSupportDashboard from './dashboards/ITSupportDashboard';
import ManagerDashboard from './dashboards/ManagerDashboard';
import HRDashboard from './dashboards/HRDashboard';
import './workspace.css';

const pages = {ADMIN: AdminDashboard, LECTURER: LecturerDashboard, STUDENT: StudentDashboard, ACCOUNTANT: AccountantDashboard, IT_SUPPORT: ITSupportDashboard, MANAGER: ManagerDashboard, HR: HRDashboard};
const roleIcons = {ADMIN: ShieldCheck, LECTURER: BookOpen, STUDENT: CalendarDays, ACCOUNTANT: Wallet, IT_SUPPORT: Wrench, MANAGER: Users, HR: BriefcaseBusiness};
const panelIcon = key => /schedule|session/i.test(key) ? CalendarDays : /class|student|enroll/i.test(key) ? Users : /equipment|device|support/i.test(key) ? Wrench : /payment|finance|contract/i.test(key) ? Wallet : /activ|audit/i.test(key) ? Activity : FileText;
const initials = name => (name || '').trim().split(/\s+/).slice(-2).map(part => part[0]).join('').toUpperCase() || 'ER';

export function Brand() {
 return <div className="erhms-brand"><span><Building2 size={22}/></span><div><b>ERHMS</b><small>TRUNG TÂM ĐÀO TẠO</small></div></div>;
}

export default function RoleWorkspace({user, onUserUpdate, onLogout}) {
 const role = resolveRole(user), config = dashboardRoles[role], Dashboard = pages[role];
 const [path, setPath] = useState(window.location.pathname.replace(/\/$/, '') || '/');
 const [data, setData] = useState(null), [busy, setBusy] = useState(true), [error, setError] = useState(''), [reload, setReload] = useState(0);
 const [logoutError, setLogoutError] = useState(''), [signingOut, setSigningOut] = useState(false), [menuOpen, setMenuOpen] = useState(false), [tasksOpen, setTasksOpen] = useState(false);
 const [pendingPanel, setPendingPanel] = useState(null);
 const notificationRef = useRef(null), mainRef = useRef(null);
 const section = workspaceRoute(path, user);

 function navigate(next, replace = false) {
  const target = next === 'recruitment' ? '/recruitment' : next === 'profile' ? '/profile' : next === 'dashboard' ? dashboardPath(user) : next;
  window.history[replace ? 'replaceState' : 'pushState']({}, '', target);
  setPath(target); setMenuOpen(false); setTasksOpen(false);
 }
 useEffect(() => {
  const listener = () => {setPath(window.location.pathname.replace(/\/$/, '') || '/'); setMenuOpen(false); setTasksOpen(false);};
  window.addEventListener('popstate', listener);
  return () => window.removeEventListener('popstate', listener);
 }, []);
 useEffect(() => {if (section === 'redirect') navigate('dashboard', true);}, [section, role]);
 useEffect(() => {
  if (!config) {setData(null); setBusy(false); return;}
  const controller = new AbortController();
  setBusy(true); setError('');
  api('/api/dashboard/' + config.slug, {signal: controller.signal})
   .then(result => {if (!controller.signal.aborted) setData(result);})
   .catch(cause => {if (!controller.signal.aborted) {if (cause.status === 401) onLogout(); else setError(cause.message);}})
   .finally(() => {if (!controller.signal.aborted) setBusy(false);});
  return () => controller.abort();
 }, [role, reload, section]);
 useEffect(() => {
  if (role !== 'ADMIN') return;
  const refresh = () => {if (document.visibilityState === 'visible') setReload(value => value + 1);};
  window.addEventListener('focus', refresh);
  document.addEventListener('visibilitychange', refresh);
  return () => {window.removeEventListener('focus', refresh); document.removeEventListener('visibilitychange', refresh);};
 }, [role]);
 useEffect(() => {
  if (!tasksOpen) return;
  const close = event => {if (event.key === 'Escape' || (event.type === 'pointerdown' && !notificationRef.current?.contains(event.target))) setTasksOpen(false);};
  document.addEventListener('keydown', close); document.addEventListener('pointerdown', close);
  return () => {document.removeEventListener('keydown', close); document.removeEventListener('pointerdown', close);};
 }, [tasksOpen]);
 useEffect(() => {
  if (section !== 'dashboard' || !pendingPanel || busy || !data) return;
  const panel = document.getElementById('dash-panel-' + pendingPanel);
  if (panel) {panel.scrollIntoView({behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth', block: 'center'}); panel.focus({preventScroll: true});}
  setPendingPanel(null);
 }, [section, pendingPanel, busy, data]);
 useEffect(() => {if (!pendingPanel) mainRef.current?.focus({preventScroll: true});}, [section]);

 async function signout() {
  if (signingOut) return;
  setSigningOut(true); setLogoutError('');
  try {await logout(); onLogout();} catch (cause) {if (cause.status === 401) onLogout(); else setLogoutError(cause.message);} finally {setSigningOut(false);}
 }
 function showPanel(key) {setPendingPanel(key); if (section !== 'dashboard') navigate('dashboard'); else {setMenuOpen(false); setTasksOpen(false);}}
 const panels = data?.panels || [], tasks = data?.tasks || [];
 const taskCount = tasks.reduce((sum, task) => sum + (Number(task.count) || 0), 0);
 const RoleIcon = roleIcons[role] || CircleHelp;

 return <div className={'erhms-workspace' + (menuOpen ? ' erhms-menu-open' : '')}>
  <a className="erhms-skip" href="#workspace-main">Đến nội dung chính</a>
  {menuOpen && <button className="erhms-backdrop" aria-label="Đóng menu" onClick={() => setMenuOpen(false)}/>}
  <aside className="erhms-sidebar" aria-label="Điều hướng chính">
   <div className="erhms-brand-row"><Brand/><button className="erhms-mobile-close icon-btn" onClick={() => setMenuOpen(false)} aria-label="Đóng menu"><X size={19}/></button></div>
   <p className="erhms-nav-label">KHÔNG GIAN LÀM VIỆC</p>
   <nav>
    <button className={section === 'dashboard' ? 'active' : ''} aria-current={section === 'dashboard' ? 'page' : undefined} onClick={() => navigate('dashboard')}><LayoutDashboard size={17}/>Dashboard</button>
    {role === 'ADMIN' && <button className={section === 'users' ? 'active' : ''} aria-current={section === 'users' ? 'page' : undefined} onClick={() => navigate('/admin/users')}><Users size={17}/>Tài khoản người dùng</button>}
    {panels.map(panel => {const Icon = panelIcon(panel.key); return <button key={panel.key} onClick={() => showPanel(panel.key)}><Icon size={17}/><span>{panel.title}</span></button>;})}
    {['HR', 'MANAGER'].includes(role) && <button className={section === 'recruitment' ? 'active' : ''} aria-current={section === 'recruitment' ? 'page' : undefined} onClick={() => navigate('recruitment')}><BriefcaseBusiness size={17}/>Nhu cầu tuyển dụng</button>}
   </nav>
   <p className="erhms-nav-label erhms-personal-label">CÁ NHÂN</p>
   <nav><button className={section === 'profile' ? 'active' : ''} aria-current={section === 'profile' ? 'page' : undefined} onClick={() => navigate('profile')}><UserRound size={17}/>Thông tin tài khoản</button></nav>
   <div className="erhms-sidebar-bottom"><div className="erhms-sidebar-note"><b><i/>Một hành trình học nghề</b><p>Kết nối tuyển sinh, đào tạo<br/>và quản lý thiết bị.</p></div>
    <div className="erhms-account"><button className="erhms-account-user" onClick={() => navigate('profile')} title="Thông tin tài khoản"><span className="erhms-avatar">{initials(user.fullName)}</span><span><b>{user.fullName}</b><small>{config?.label || 'Vai trò không hợp lệ'}</small></span></button><button className="icon-btn" onClick={signout} disabled={signingOut} aria-label="Đăng xuất" title="Đăng xuất"><LogOut size={17}/></button></div>
   </div>
  </aside>
  <div className="erhms-main-wrap">
   <header className="erhms-topbar"><button className="erhms-mobile-toggle icon-btn" onClick={() => setMenuOpen(value => !value)} aria-expanded={menuOpen} aria-label="Mở menu"><Menu size={21}/></button><div className="erhms-breadcrumb">Không gian làm việc <ChevronRight size={12}/><b>{section === 'profile' ? 'Thông tin tài khoản' : section === 'users' ? 'Tài khoản người dùng' : section === 'recruitment' ? 'Tuyển dụng' : 'Dashboard'}</b></div>
    <div className="erhms-topbar-actions"><time className="erhms-date" dateTime={new Date().toISOString()}><CalendarDays size={14}/>{new Intl.DateTimeFormat('vi-VN', {weekday: 'long', day: 'numeric', month: 'long', year: 'numeric', timeZone: 'Asia/Ho_Chi_Minh'}).format(new Date())}</time><div className="erhms-notification" ref={notificationRef}><button className="icon-btn" aria-label={'Việc cần xử lý: ' + taskCount} aria-expanded={tasksOpen} aria-controls="workspace-tasks" onClick={() => setTasksOpen(value => !value)}><Bell size={18}/>{taskCount > 0 && <i/>}</button>{tasksOpen && <div id="workspace-tasks" className="erhms-task-popover"><b>Việc cần chú ý</b>{tasks.length ? tasks.map(task => <div className="erhms-task-preview" key={task.key}><span>{task.title}</span><strong>{task.count.toLocaleString('vi-VN')}</strong></div>) : <p>Chưa có công việc cần xử lý.</p>}<small>Cập nhật cùng dữ liệu dashboard.</small></div>}</div><button className="erhms-avatar" onClick={() => navigate('profile')} aria-label="Mở thông tin tài khoản">{initials(user.fullName)}</button></div>
   </header>
   <main id="workspace-main" className="erhms-main" ref={mainRef} tabIndex={-1}>
    {logoutError && <div className="error" role="alert">{logoutError}</div>}
    {section === 'profile' ? <UserProfile user={user} onUserUpdate={onUserUpdate} onLogout={onLogout} onBack={() => navigate('dashboard')}/>
     : section === 'users' ? <UserAccounts user={user} onUserUpdate={onUserUpdate} onLogout={onLogout} onAccountsChanged={() => setReload(value => value + 1)}/>
     : section === 'recruitment' ? <RecruitmentPage user={user} onLogout={onLogout}/>
     : ['forbidden', 'unsupported', 'missing'].includes(section) ? <section className="erhms-state" role={section === 'unsupported' ? 'alert' : undefined}><RoleIcon size={36}/><h1>{section === 'forbidden' ? 'Bạn không có quyền xem dashboard này' : section === 'unsupported' ? 'Vai trò không hợp lệ' : 'Không tìm thấy trang'}</h1><p>{section === 'unsupported' ? roleError(user) : 'Dashboard được mở theo vai trò của tài khoản đang đăng nhập.'}</p>{config && <button onClick={() => navigate('dashboard', true)}>Về dashboard của tôi</button>}</section>
     : busy ? <section className="erhms-state" role="status"><RefreshCw size={26} className="erhms-spinning"/><p>Đang tải dashboard…</p></section>
     : error ? <section className="erhms-state"><CircleHelp size={34}/><h1>Chưa tải được dashboard</h1><p role="alert">{error}</p><button onClick={() => setReload(value => value + 1)}><RefreshCw size={16}/>Thử lại</button></section>
     : data && Dashboard ? <><Dashboard data={data} user={user} onNavigate={navigate}/><div className="erhms-refresh"><span><RoleIcon size={13}/>{config.category}</span><button onClick={() => setReload(value => value + 1)}><RefreshCw size={13}/>Cập nhật dữ liệu</button></div></> : null}
   </main>
   <footer className="erhms-footer"><span><ShieldCheck size={13}/>ERHMS · Kết nối tuyển sinh, đào tạo và quản lý thiết bị.</span><span>{data?.updatedAt ? 'Cập nhật: ' + new Date(data.updatedAt).toLocaleString('vi-VN', {timeZone: 'Asia/Ho_Chi_Minh'}) : ''}</span></footer>
  </div>
 </div>;
}
