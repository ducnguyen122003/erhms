import {useEffect,useRef,useState} from 'react';
import {ArrowLeft,Check,KeyRound,Save,UserRound} from 'lucide-react';
import {api} from './api';

const valuesOf=user=>({fullName:user.fullName||'',email:user.email||'',phone:user.phone||''});
const roleNames={HR:'Nhân sự doanh nghiệp',EMPLOYER_HR:'Nhân sự doanh nghiệp',SERVICE_MANAGER:'Quản lý dịch vụ',ADMIN:'Quản trị viên',USER:'Người dùng',WORKER:'Người lao động'};
const emptyPasswords=()=>({oldPassword:'',newPassword:'',confirmPassword:''});

export default function UserProfile({user,onUserUpdate,onLogout,onBack}){
 const [form,setForm]=useState(()=>valuesOf(user)),[loading,setLoading]=useState(true),[saving,setSaving]=useState(false);
 const [error,setError]=useState(''),[fields,setFields]=useState({}),[notice,setNotice]=useState(''),[reload,setReload]=useState(0);
 const [passwords,setPasswords]=useState(emptyPasswords),[changingPassword,setChangingPassword]=useState(false);
 const [passwordError,setPasswordError]=useState(''),[passwordFields,setPasswordFields]=useState({}),[passwordNotice,setPasswordNotice]=useState('');
 const heading=useRef(null),formRef=useRef(null),passwordFormRef=useRef(null),mounted=useRef(false),pending=useRef(false),sessionExpired=useRef(false);
 const busy=loading||saving||changingPassword;

 useEffect(()=>{mounted.current=true;heading.current?.focus();return()=>{mounted.current=false;};},[]);
 useEffect(()=>{
  let active=true;
  setLoading(true);setError('');setNotice('');
  api('/api/auth/me').then(current=>{
   if(active&&!sessionExpired.current){setForm(valuesOf(current));onUserUpdate(current);}
  }).catch(e=>{
   if(active&&!sessionExpired.current){if(e.status===401){sessionExpired.current=true;onLogout();}else setError(e.message);}
  }).finally(()=>{if(active&&!sessionExpired.current)setLoading(false);});
  return()=>{active=false;};
 },[reload]);

 function update(key,value){setForm(current=>({...current,[key]:value}));setFields(current=>({...current,[key]:null}));setNotice('');}
 async function save(event){
  event.preventDefault();
  if(busy||pending.current||sessionExpired.current)return;
  const input={fullName:form.fullName.trim(),phone:form.phone.replace(/[\s().-]/g,'')};
  const invalid={};
  if(input.fullName.length<2||input.fullName.length>120)invalid.fullName='Họ và tên cần từ 2 đến 120 ký tự.';
  if(input.phone&&! /^(?:0[0-9]{9}|\+?84[0-9]{9})$/.test(input.phone))invalid.phone='Nhập số điện thoại Việt Nam hợp lệ, ví dụ 0912345678 hoặc +84912345678.';
  setFields(invalid);setError('');setNotice('');
  if(Object.keys(invalid).length){formRef.current?.elements.namedItem(Object.keys(invalid)[0])?.focus();return;}
  pending.current=true;setSaving(true);
  try{
   const current=await api('/api/auth/me',{method:'PUT',body:JSON.stringify(input)});
   if(!mounted.current||sessionExpired.current)return;
   onUserUpdate(current);setForm(valuesOf(current));setNotice('Đã cập nhật thông tin cá nhân.');
  }catch(e){
   if(!mounted.current||sessionExpired.current)return;
   if(e.status===401){sessionExpired.current=true;onLogout();return;}
   setError(e.message);setFields(e.fields||{});
   formRef.current?.elements.namedItem(Object.keys(e.fields||{})[0])?.focus();
  }finally{pending.current=false;if(mounted.current&&!sessionExpired.current)setSaving(false);}
 }

 function updatePassword(key,value){setPasswords(current=>({...current,[key]:value}));setPasswordFields({});setPasswordError('');setPasswordNotice('');}
 async function changePassword(event){
  event.preventDefault();
  if(busy||pending.current||sessionExpired.current)return;
  const invalid={};
  if(!passwords.oldPassword)invalid.oldPassword='Vui lòng nhập mật khẩu hiện tại.';
  if(!passwords.newPassword)invalid.newPassword='Vui lòng nhập mật khẩu mới.';
  else if([...passwords.newPassword].length<8)invalid.newPassword='Mật khẩu mới cần ít nhất 8 ký tự.';
  else if(new TextEncoder().encode(passwords.newPassword).length>72)invalid.newPassword='Mật khẩu mới tối đa 72 byte UTF-8. Vui lòng dùng mật khẩu ngắn hơn.';
  if(!passwords.confirmPassword)invalid.confirmPassword='Vui lòng nhập lại mật khẩu mới.';
  else if(passwords.newPassword!==passwords.confirmPassword)invalid.confirmPassword='Mật khẩu xác nhận chưa khớp với mật khẩu mới.';
  setPasswordFields(invalid);setPasswordError('');setPasswordNotice('');
  if(Object.keys(invalid).length){passwordFormRef.current?.elements.namedItem(Object.keys(invalid)[0])?.focus();return;}
  pending.current=true;setChangingPassword(true);
  try{
   await api('/api/auth/change-password',{method:'POST',body:JSON.stringify(passwords)});
   if(!mounted.current||sessionExpired.current)return;
   setPasswords(emptyPasswords());setPasswordNotice('Đã đổi mật khẩu. Lần đăng nhập tiếp theo dùng mật khẩu mới.');
  }catch(e){
   if(!mounted.current||sessionExpired.current)return;
   if(e.status===401){sessionExpired.current=true;onLogout();return;}
   setPasswordError(e.message);setPasswordFields(e.fields||{});
   passwordFormRef.current?.elements.namedItem(Object.keys(e.fields||{})[0])?.focus();
  }finally{pending.current=false;if(mounted.current&&!sessionExpired.current)setChangingPassword(false);}
 }

 const field=(key,label,type,props={})=><label htmlFor={'profile-'+key}>{label}<input id={'profile-'+key} name={key} type={type} value={form[key]} onChange={e=>update(key,e.target.value)} aria-invalid={Boolean(fields[key])} aria-describedby={fields[key]?'profile-'+key+'-error':undefined} {...props}/>{fields[key]&&<small id={'profile-'+key+'-error'} className="field-error">{fields[key]}</small>}</label>;
 const passwordField=(key,label,autoComplete)=><label htmlFor={'password-'+key}>{label}<input id={'password-'+key} name={key} type="password" value={passwords[key]} onChange={e=>updatePassword(key,e.target.value)} required autoComplete={autoComplete} aria-invalid={Boolean(passwordFields[key])} aria-describedby={passwordFields[key]?'password-'+key+'-error':undefined}/>{passwordFields[key]&&<small id={'password-'+key+'-error'} className="field-error">{passwordFields[key]}</small>}</label>;
 return <section className="user-profile" aria-labelledby="profile-heading">
  <button type="button" className="profile-back text-button" onClick={onBack} disabled={saving||changingPassword}><ArrowLeft size={16}/>Quay lại</button>
  <div className="page-heading"><div><span className="eyebrow">TÀI KHOẢN / THÔNG TIN CÁ NHÂN</span><h1 id="profile-heading" ref={heading} tabIndex={-1}>Thông tin cá nhân</h1><p className="muted">Xem và cập nhật thông tin liên hệ của bạn.</p></div></div>
  <div className="profile-layout">
   <aside className="profile-summary"><span className="profile-portrait"><UserRound size={34}/></span><h2>{user.fullName}</h2><p>{roleNames[user.role]||user.role}</p><span>Mã người dùng: {user.id}</span></aside>
   <div className="profile-forms">
   <section className="profile-panel" aria-labelledby="profile-contact-heading">
    <h2 id="profile-contact-heading">Thông tin liên hệ</h2>
    {loading&&<p role="status" className="muted">Đang tải thông tin cá nhân…</p>}
    {error&&<div className="error" role="alert">{error}<button type="button" className="text-button" onClick={()=>setReload(value=>value+1)} disabled={busy}>Tải lại thông tin</button></div>}
    {notice&&<div className="success" role="status"><Check size={17}/>{notice}</div>}
    <form ref={formRef} onSubmit={save} noValidate aria-busy={busy}>
     <fieldset disabled={busy}>
      {field('fullName','Họ và tên *','text',{required:true,minLength:2,maxLength:120,autoComplete:'name'})}
      {field('email','Email đăng nhập','email',{readOnly:true,autoComplete:'email','aria-describedby':'profile-email-hint'})}
      {field('phone','Số điện thoại','tel',{maxLength:20,autoComplete:'tel',placeholder:'Ví dụ: 0912345678'})}
      <p id="profile-email-hint" className="muted profile-hint">* Thông tin bắt buộc. Email dùng để đăng nhập và không thể thay đổi.</p>
      <div className="profile-actions"><button type="submit"><Save size={17}/>{saving?'Đang lưu…':'Lưu thay đổi'}</button></div>
     </fieldset>
    </form>
   </section>
   <section className="profile-panel" aria-labelledby="profile-password-heading">
    <h2 id="profile-password-heading">Đổi mật khẩu</h2>
    {passwordError&&<div className="error" role="alert">{passwordError}</div>}
    {passwordNotice&&<div className="success" role="status"><Check size={17}/>{passwordNotice}</div>}
    <form ref={passwordFormRef} onSubmit={changePassword} noValidate aria-busy={busy}>
     <fieldset disabled={busy}>
      {passwordField('oldPassword','Mật khẩu hiện tại *','current-password')}
      {passwordField('newPassword','Mật khẩu mới *','new-password')}
      {passwordField('confirmPassword','Xác nhận mật khẩu mới *','new-password')}
      <p className="muted profile-hint">* Thông tin bắt buộc. Mật khẩu mới cần ít nhất 8 ký tự.</p>
      <div className="profile-actions"><button type="submit"><KeyRound size={17}/>{changingPassword?'Đang đổi mật khẩu…':'Đổi mật khẩu'}</button></div>
     </fieldset>
    </form>
   </section>
   </div>
  </div>
 </section>;
}
