import {useEffect, useState} from 'react';
import {createRoot} from 'react-dom/client';
import {RefreshCw} from 'lucide-react';
import {api, logout, refreshCsrf} from './api';
import RoleWorkspace from './RoleWorkspace';
import Login from './Login';
import './style.css';

function App() {
 const [user, setUser] = useState(null), [loading, setLoading] = useState(true), [bootError, setBootError] = useState(null);
 async function boot() {
  setLoading(true); setBootError(null);
  try {await refreshCsrf(); setUser(await api('/api/auth/me'));}
  catch (cause) {if (cause.status !== 401) setBootError(cause);}
  finally {setLoading(false);}
 }
 useEffect(() => {boot();}, []);
 function clearSession() {window.history.replaceState({}, '', '/login'); setUser(null); setBootError(null);}
 async function switchAccount() {
  setLoading(true);
  try {await logout(); clearSession();}
  catch (cause) {if (cause.status === 401) clearSession(); else setBootError(cause);}
  finally {setLoading(false);}
 }
 if (loading) return <div className="loading" role="status">Đang kết nối ERHMS…</div>;
 if (bootError) return <div className="loading"><h2>{bootError.status === 403 ? bootError.code === 'INVALID_ROLE' ? 'Vai trò không hợp lệ' : 'Không có quyền truy cập' : 'Chưa kết nối được hệ thống'}</h2><p role="alert">{bootError.message}</p><button onClick={boot}><RefreshCw size={16}/>Thử lại</button>{bootError.status === 403 && <button className="text-button" onClick={switchAccount}>Đăng nhập tài khoản khác</button>}</div>;
 return user ? <RoleWorkspace key={user.id + ':' + user.roleId + ':' + user.role} user={user} onUserUpdate={updated => setUser(current => current && current.id === updated.id ? updated : current)} onLogout={clearSession}/> : <Login onLogin={setUser}/>;
}

createRoot(document.getElementById('root')).render(<App/>);
