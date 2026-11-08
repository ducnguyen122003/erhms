import {useEffect,useRef,useState} from 'react';
import {RefreshCw} from 'lucide-react';
import {api} from '../api';
import useAdminData from './useAdminData';
import {AdminAside,AdminFormFooter,AdminLoading,AdminMessage,AdminPage} from './AdminUI';

export default function SystemSettingsPage({user,onLogout,onNavigate,onChanged}) {
 const remote=useAdminData(signal=>api('/api/admin/settings',{signal}),[],onLogout);
 const [form,setForm]=useState({centerName:'',notificationEmail:'',defaultLanguage:'vi'}),[error,setError]=useState(''),[notice,setNotice]=useState(''),[saving,setSaving]=useState(false),[testing,setTesting]=useState(false);
 const pending=useRef(false),mounted=useRef(false),request=useRef(null);
 useEffect(()=>{mounted.current=true;return()=>{mounted.current=false;request.current?.abort();};},[]);
 useEffect(()=>{if(remote.data)setForm({centerName:remote.data.centerName,notificationEmail:remote.data.notificationEmail,defaultLanguage:remote.data.defaultLanguage});},[remote.data]);
 const update=(key,value)=>{setForm(current=>({...current,[key]:value}));setNotice('');setError('');};
 async function save(event){
  event.preventDefault();if(pending.current)return;const controller=new AbortController();request.current=controller;pending.current=true;setSaving(true);setError('');setNotice('');
  try{const result=await api('/api/admin/settings',{method:'PUT',body:JSON.stringify({...form,centerName:form.centerName.trim(),notificationEmail:form.notificationEmail.trim(),version:remote.data.version}),signal:controller.signal});if(!mounted.current||controller.signal.aborted)return;remote.setData(result);setNotice('Đã lưu cấu hình hệ thống.');window.dispatchEvent(new Event('erhms-branding-changed'));onChanged?.();}
  catch(cause){if(!mounted.current||controller.signal.aborted)return;if(cause.status===401)onLogout();else setError(cause.message);}
  finally{pending.current=false;if(mounted.current&&!controller.signal.aborted)setSaving(false);}
 }
 async function testEmail(){
  if(pending.current)return;const controller=new AbortController();request.current=controller;pending.current=true;setTesting(true);setError('');setNotice('');
  try{const result=await api('/api/admin/settings/test-email',{method:'POST',signal:controller.signal});if(mounted.current&&!controller.signal.aborted)setNotice(result.message);}
  catch(cause){if(!mounted.current||controller.signal.aborted)return;if(cause.status===401)onLogout();else setError(cause.message);}
  finally{pending.current=false;if(mounted.current&&!controller.signal.aborted)setTesting(false);}
 }
 return <AdminPage title="Cấu hình hệ thống" description="Thiết lập kết nối và tham số vận hành của BK-VTCMS." code="SystemConfiguration">
  {remote.loading||remote.error?<AdminLoading error={remote.error} onRetry={remote.retry}/>:<div className="admin-editor-grid"><form className="admin-card admin-form-card" onSubmit={save} aria-busy={saving}>
   <div className="admin-form-content"><h2>Thông tin cần cập nhật</h2><p className="admin-muted">Các trường có dấu * là bắt buộc.</p><AdminMessage error={error} notice={notice}/><fieldset disabled={saving||testing}><h3>Thông tin trung tâm</h3><div className="admin-field-grid"><label className="admin-field" htmlFor="settings-center">Tên đơn vị *<input id="settings-center" name="centerName" value={form.centerName} onChange={e=>update('centerName',e.target.value)} required maxLength={160}/></label><label className="admin-field" htmlFor="settings-email">Email thông báo *<input id="settings-email" type="email" name="notificationEmail" value={form.notificationEmail} onChange={e=>update('notificationEmail',e.target.value)} required maxLength={190}/></label><label className="admin-field">Múi giờ<input value={remote.data.timezone} readOnly disabled/></label><label className="admin-field" htmlFor="settings-language">Ngôn ngữ mặc định<select id="settings-language" value={form.defaultLanguage} onChange={e=>update('defaultLanguage',e.target.value)}><option value="vi">Tiếng Việt</option></select></label></div>
    <h3>Kết nối dịch vụ</h3><div className="admin-field-grid"><label className="admin-field">Gmail / Email<input value={remote.data.mailStatus==='CONFIGURED'?'Đã cấu hình — cần kiểm tra kết nối':'Chưa cấu hình'} disabled readOnly/></label><label className="admin-field">Banking<input value="Chưa cấu hình" disabled readOnly/></label><label className="admin-field">Mã tham chiếu thanh toán<input value={remote.data.paymentReference} disabled readOnly/></label><label className="admin-field">Thời hạn khôi phục mật khẩu<input value={remote.data.passwordResetPolicy} disabled readOnly/></label></div>
    <button type="button" className="admin-secondary admin-smtp-check" onClick={testEmail} disabled={remote.data.mailStatus!=='CONFIGURED'}><RefreshCw size={14}/>{testing?'Đang kiểm tra…':'Kiểm tra kết nối email'}</button><p className="admin-muted">Thông tin truy cập Gmail được cấu hình tại máy chủ. Kiểm tra kết nối không gửi thư thử.</p></fieldset></div>
   <AdminFormFooter onBack={()=>onNavigate('dashboard')} busy={saving||testing} label="Lưu cấu hình"/>
  </form><AdminAside user={user} updatedBy={remote.data.updatedBy} updatedAt={remote.data.updatedAt} tips={['Thông tin truy cập dịch vụ được giữ tại máy chủ.','Thử kết nối trước khi thay đổi cấu hình vận hành.']}/></div>}
 </AdminPage>;
}
