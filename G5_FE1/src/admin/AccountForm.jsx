import {useEffect,useRef,useState} from 'react';
import {Trash2} from 'lucide-react';
import {api} from '../api';
import {resetPasswordError} from '../authValidation';
import useAdminData from './useAdminData';
import {AdminAside,AdminDialog,AdminFormFooter,AdminLoading,AdminMessage,AdminPage,accountCode,statusValue} from './AdminUI';

const empty={fullName:'',email:'',phone:'',roleId:'',status:'ACTIVE',password:'',confirmPassword:'',changeNote:''};
export default function AccountForm({accountId,user,onNavigate,onLogout,onUserUpdate,onChanged,initialNotice=''}) {
 const editing=Boolean(accountId),[form,setForm]=useState(empty),[fields,setFields]=useState({}),[error,setError]=useState(''),[notice,setNotice]=useState(initialNotice),[saving,setSaving]=useState(false),[deleting,setDeleting]=useState(false);
 const pending=useRef(false),mounted=useRef(false),request=useRef(null),formRef=useRef(null);
 const remote=useAdminData(signal=>Promise.all([api('/api/admin/roles',{signal}),editing?api('/api/admin/users/'+accountId,{signal}):Promise.resolve(null)]).then(([roles,account])=>({roles,account})),[accountId],onLogout);
 useEffect(()=>{mounted.current=true;return()=>{mounted.current=false;request.current?.abort();};},[]);
 useEffect(()=>{if(remote.data)setForm({...empty,...(remote.data.account?{...remote.data.account,phone:remote.data.account.phone??'',roleId:String(remote.data.account.roleId),status:statusValue(remote.data.account)}:{})});},[remote.data]);
 const account=remote.data?.account,isSelf=editing&&String(accountId)===String(user.id);
 const update=(key,value)=>{setForm(current=>({...current,[key]:value}));setError('');setNotice('');setFields(current=>({...current,[key]:undefined}));};
 async function save(event) {
  event.preventDefault();if(pending.current)return;
  const values={fullName:form.fullName.trim(),email:form.email.trim(),phone:(form.phone??'').replace(/[\s().-]/g,''),roleId:Number(form.roleId)},invalid={};
  if(values.fullName.length<2||values.fullName.length>120)invalid.fullName='Họ và tên cần từ 2 đến 120 ký tự.';
  if(!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(values.email)||values.email.length>190)invalid.email='Nhập email đăng nhập hợp lệ.';
  if(values.phone&&!/^(?:0[0-9]{9}|\+?84[0-9]{9})$/.test(values.phone))invalid.phone='Số điện thoại không hợp lệ.';
  if(!remote.data.roles.some(role=>role.id===values.roleId))invalid.roleId='Chọn vai trò hợp lệ.';
  if(!editing||form.password||form.confirmPassword){const passwordError=resetPasswordError(form.password,form.confirmPassword);if(passwordError)invalid.password=passwordError;values.password=form.password;values.confirmPassword=form.confirmPassword;}
  setFields(invalid);setError('');setNotice('');
  if(Object.keys(invalid).length){formRef.current?.elements.namedItem(Object.keys(invalid)[0])?.focus();return;}
  const controller=new AbortController();request.current=controller;pending.current=true;setSaving(true);
  try {
   const saved=await api(editing?'/api/admin/users/'+accountId+'/details':'/api/admin/users',{method:editing?'PUT':'POST',body:JSON.stringify(editing?{account:values,status:form.status,changeNote:form.changeNote,version:account.version}:values),signal:controller.signal});
   if(!mounted.current||controller.signal.aborted)return;
   onChanged?.();
   if(isSelf){if(values.email.toLowerCase()!==user.email.toLowerCase()||form.password){onLogout();return;}onUserUpdate({...user,...saved});}
   if(!editing){onNavigate('/admin/users/'+saved.id+'/edit',{notice:'Tạo tài khoản thành công.'});return;}
   remote.setData(current=>({...current,account:saved}));setNotice('Đã lưu tài khoản và ghi nhận thay đổi vào nhật ký hệ thống.');
  }catch(cause){if(!mounted.current||controller.signal.aborted)return;if(cause.status===401){onLogout();return;}setError(cause.message);const mapped={};for(const [key,value] of Object.entries(cause.fields||{}))mapped[key.replace(/^account\./,'')]=value;setFields(mapped);}
  finally{pending.current=false;if(mounted.current&&!controller.signal.aborted)setSaving(false);}
 }
 async function remove() {
  if(pending.current)return;const controller=new AbortController();request.current=controller;pending.current=true;setSaving(true);setError('');
  try{await api('/api/admin/users/'+accountId,{method:'DELETE',signal:controller.signal});if(mounted.current&&!controller.signal.aborted){onChanged?.();onNavigate('/admin/users');}}
  catch(cause){if(!mounted.current||controller.signal.aborted)return;if(cause.status===401)onLogout();else setError(cause.message);}
  finally{pending.current=false;if(mounted.current&&!controller.signal.aborted)setSaving(false);}
 }
 const field=(key,label,type='text',props={})=><label className="admin-field" htmlFor={'account-'+key}>{label}<input id={'account-'+key} name={key} type={type} value={form[key]} onChange={e=>update(key,e.target.value)} aria-invalid={Boolean(fields[key])} aria-describedby={fields[key]?'account-'+key+'-error':undefined} {...props}/>{fields[key]&&<small className="field-error" id={'account-'+key+'-error'}>{fields[key]}</small>}</label>;
 return <AdminPage title={editing?'Cập nhật tài khoản người dùng':'Tạo / cấp tài khoản'} description="Thiết lập vai trò và trạng thái truy cập theo phân công." code={editing?'EditUser':'CreateUser'}>
  {remote.loading||remote.error?<AdminLoading error={remote.error} onRetry={remote.retry}/>:<div className="admin-editor-grid"><form className="admin-card admin-form-card" ref={formRef} onSubmit={save} noValidate aria-busy={saving}>
   <div className="admin-form-content"><h2>Thông tin cần cập nhật</h2><p className="admin-muted">Các trường có dấu * là bắt buộc.</p><AdminMessage error={deleting?'':error} notice={notice}/><fieldset disabled={saving}><h3>Thông tin tài khoản</h3><div className="admin-field-grid">
    {editing&&<label className="admin-field">Mã tài khoản<input value={accountCode(accountId)} disabled readOnly/></label>}{field('fullName','Họ và tên *','text',{maxLength:120,required:true,autoComplete:'name'})}{field('email','Email đăng nhập *','email',{maxLength:190,required:true,autoComplete:'username'})}{field('phone','Số điện thoại','tel',{maxLength:20,autoComplete:'tel'})}
   </div><h3>Quyền truy cập</h3><div className="admin-field-grid"><label className="admin-field" htmlFor="account-roleId">Vai trò *<select id="account-roleId" name="roleId" value={form.roleId} onChange={e=>update('roleId',e.target.value)} disabled={isSelf} required><option value="" disabled>Chọn vai trò</option>{remote.data.roles.map(role=><option key={role.id} value={role.id}>{role.name}</option>)}</select>{fields.roleId&&<small className="field-error">{fields.roleId}</small>}</label>{editing&&<label className="admin-field" htmlFor="account-status">Trạng thái *<select id="account-status" value={form.status} onChange={e=>update('status',e.target.value)} disabled={isSelf}><option value="ACTIVE">Hoạt động</option><option value="DISABLED">Vô hiệu hóa</option><option value="LOCKED">Bị khóa</option><option value="DISABLED_LOCKED">Vô hiệu hóa và bị khóa</option></select></label>}</div>
   {isSelf&&<p className="admin-muted">Bạn giữ nguyên quyền hạn và trạng thái của tài khoản đang đăng nhập.</p>}
   {editing&&<label className="admin-field" htmlFor="account-note">Ghi chú thay đổi<textarea id="account-note" value={form.changeNote} maxLength={1000} onChange={e=>update('changeNote',e.target.value)} placeholder="Ghi nhận lý do hoặc nội dung cập nhật" rows={3}/></label>}
   {editing?<details className="admin-password-section"><summary>Đổi mật khẩu (tùy chọn)</summary><p className="admin-muted">Để trống để giữ mật khẩu hiện tại. Sau khi đổi mật khẩu hoặc email của mình, bạn cần đăng nhập lại.</p><div className="admin-field-grid">{field('password','Mật khẩu mới','password',{autoComplete:'new-password'})}{field('confirmPassword','Xác nhận mật khẩu mới','password',{autoComplete:'new-password'})}</div></details>:<><h3>Thông tin đăng nhập</h3><div className="admin-field-grid">{field('password','Mật khẩu *','password',{autoComplete:'new-password'})}{field('confirmPassword','Xác nhận mật khẩu *','password',{autoComplete:'new-password'})}</div><p className="admin-muted">Từ 8 ký tự, tối đa 72 byte UTF-8. Người dùng đăng nhập được bằng mật khẩu vừa tạo.</p></>}
   {fields.status&&<p className="field-error">{fields.status}</p>}</fieldset></div>
   <AdminFormFooter onBack={()=>onNavigate('/admin/users')} busy={saving} label={editing?'Lưu tài khoản':'Tạo tài khoản'}>{editing&&!isSelf&&<button type="button" className="admin-danger-link" onClick={()=>{setError('');setDeleting(true);}} disabled={saving}><Trash2 size={14}/>Xóa</button>}</AdminFormFooter>
  </form><AdminAside user={user} tips={['Chỉ cấp vai trò phù hợp nhiệm vụ được phân công.','Khóa hoặc vô hiệu hóa tài khoản sẽ kết thúc quyền truy cập của người dùng.']}/></div>}
  {deleting&&<AdminDialog title="Xóa tài khoản?" onClose={()=>{setDeleting(false);setError('');}} busy={saving}><div className="admin-dialog-body"><p>Bạn sắp xóa tài khoản <strong>{account?.fullName}</strong> ({account?.email}). Thao tác không thể hoàn tác.</p><p className="admin-muted">Tài khoản có dữ liệu nghiệp vụ sẽ được giữ lại. Bạn có thể khóa hoặc vô hiệu hóa để ngừng truy cập.</p><AdminMessage error={error}/><div className="admin-dialog-actions"><button type="button" className="admin-secondary" onClick={()=>{setDeleting(false);setError('');}} disabled={saving} data-initial-focus>Hủy</button><button type="button" className="admin-danger" onClick={remove} disabled={saving}>{saving?'Đang xóa…':'Xóa tài khoản'}</button></div></div></AdminDialog>}
 </AdminPage>;
}
