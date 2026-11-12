import {useEffect,useRef,useState} from 'react';
import {Check,ShieldCheck,RefreshCw} from 'lucide-react';
import {api} from '../api';
import useAdminData from './useAdminData';
import {AdminLoading,AdminMessage,AdminPage} from './AdminUI';

export default function RolePermissionsPage({onLogout,onChanged}) {
 const remote=useAdminData(signal=>api('/api/admin/permissions',{signal}),[],onLogout);
 const [selected,setSelected]=useState(new Set()),[saving,setSaving]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState('');
 const pending=useRef(false),mounted=useRef(false),request=useRef(null);
 useEffect(()=>{mounted.current=true;return()=>{mounted.current=false;request.current?.abort();};},[]);
 useEffect(()=>{if(remote.data)setSelected(new Set(remote.data.grants.map(grant=>grant.roleId+':'+grant.functionCode)));},[remote.data]);
 const original=new Set(remote.data?.grants.map(grant=>grant.roleId+':'+grant.functionCode)||[]);
 const dirty=original.size!==selected.size||[...original].some(key=>!selected.has(key));
 function toggle(key){setSelected(current=>{const next=new Set(current);if(next.has(key))next.delete(key);else next.add(key);return next;});setNotice('');setError('');}
 async function save(){
  if(pending.current||!dirty)return;const controller=new AbortController();request.current=controller;pending.current=true;setSaving(true);setError('');setNotice('');
  const grants=[...selected].map(key=>{const [roleId,functionCode]=key.split(':');return {roleId:Number(roleId),functionCode};});
  try{const result=await api('/api/admin/permissions',{method:'PUT',body:JSON.stringify({version:remote.data.version,grants}),signal:controller.signal});if(!mounted.current||controller.signal.aborted)return;remote.setData(result);setNotice('Đã lưu phân quyền. Quyền truy cập mới có hiệu lực ngay.');onChanged?.();}
  catch(cause){if(!mounted.current||controller.signal.aborted)return;if(cause.status===401)onLogout();else setError(cause.message);}
  finally{pending.current=false;if(mounted.current&&!controller.signal.aborted)setSaving(false);}
 }
 return <AdminPage title="Vai trò & phân quyền" description="Thiết lập phạm vi thao tác cho 7 vai trò hệ thống." code="RolePermission">
  {remote.loading||remote.error?<AdminLoading error={remote.error} onRetry={remote.retry}/>:<section className="admin-card admin-permission-card"><header className="admin-card-heading"><div><h2>Ma trận quyền theo vai trò</h2><p>Chọn vai trò được truy cập từng màn bằng mã màn.</p></div><button type="button" onClick={save} disabled={saving||!dirty}><Check size={15}/>{saving?'Đang lưu…':'Lưu phân quyền'}</button></header><AdminMessage error={error} notice={notice}/>
   {error&&<button className="admin-text-button admin-reload" type="button" onClick={remote.retry} disabled={saving}><RefreshCw size={14}/>Tải lại phân quyền</button>}
   <div className="admin-table-scroll"><table className="admin-permission-table"><thead><tr><th>MÀN / CHỨC NĂNG</th>{remote.data.roles.map(role=><th key={role.id}>{role.name}</th>)}</tr></thead><tbody>{remote.data.functions.map(screen=><tr key={screen.code}><th scope="row">{screen.name}<small>{screen.screenCode} · {screen.code}</small></th>{remote.data.roles.map(role=>{const key=role.id+':'+screen.code,essential=role.id===1&&['USER_ACCOUNTS','ROLE_PERMISSIONS'].includes(screen.code),eligible=screen.allowedRoles.includes(role.id);return <td key={role.id}><input type="checkbox" checked={selected.has(key)} onChange={()=>toggle(key)} disabled={saving||essential||!eligible} aria-label={screen.name+' — '+role.name} title={essential?'Giữ quyền khôi phục hệ thống cho Admin':!eligible?'Vai trò này không được phép truy cập màn này':undefined}/></td>;})}</tr>)}</tbody></table></div>
   <div className="admin-permission-note"><ShieldCheck size={20}/><div><strong>Phân quyền theo màn, bảo toàn phạm vi dữ liệu</strong><p>Dashboard hiển thị dữ liệu theo vai trò. Các giới hạn lớp phụ trách, hồ sơ cá nhân và quy trình nghiệp vụ vẫn được áp dụng. Admin luôn giữ quyền tài khoản và phân quyền để quản lý hệ thống.</p></div></div>
  </section>}
 </AdminPage>;
}
