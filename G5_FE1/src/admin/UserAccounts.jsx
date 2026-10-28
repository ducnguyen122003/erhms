import {useEffect, useId, useRef, useState} from 'react';
import {Ban, Check, ChevronLeft, ChevronRight, Eye, Lock, Pencil, Plus, RefreshCw, Search, ShieldCheck, Trash2, Unlock, UserCheck, Users, X} from 'lucide-react';
import {api} from '../api';
import {resetPasswordError} from '../authValidation';
import {dashboardRoles, rolesById} from '../roles';
import './user-accounts.css';

const PAGE_SIZE = 10;
const initialForm = account => ({fullName: account?.fullName || '', email: account?.email || '', phone: account?.phone || '', roleId: account?.roleId ? String(account.roleId) : '', password: '', confirmPassword: ''});
const sameAccount = (left, right) => String(left?.id) === String(right?.id);
const statusLabel = account => !account.accountEnabled ? (account.accountLocked ? 'Vô hiệu hóa · Đang khóa' : 'Đã vô hiệu hóa') : account.accountLocked ? 'Đang khóa' : 'Hoạt động';

function useAccountDialog(requestRef) {
 const dialogRef = useRef(null), mounted = useRef(false);
 useEffect(() => {
  mounted.current = true;
  const previouslyFocused = document.activeElement, dialog = dialogRef.current;
  dialog.showModal();
  dialog.querySelector('[data-initial-focus]')?.focus();
  return () => {
   mounted.current = false;
   requestRef.current?.abort();
   dialog.close();
   if (previouslyFocused?.isConnected) previouslyFocused.focus();
   else document.getElementById('accounts-heading')?.focus();
  };
 }, []);
 return {dialogRef, mounted};
}

function AccountEditor({account, roles, user, onClose, onSaved, onLogout}) {
 const [form, setForm] = useState(() => initialForm(account));
 const [fields, setFields] = useState({}), [error, setError] = useState(''), [saving, setSaving] = useState(false);
 const formRef = useRef(null), pending = useRef(false), requestRef = useRef(null);
 const {dialogRef, mounted} = useAccountDialog(requestRef);
 const titleId = useId(), prefix = useId();
 const isSelf = Boolean(account && sameAccount(account, user));
 const emailChanging = isSelf && form.email.trim().toLowerCase() !== user.email.trim().toLowerCase();
 const passwordChanging = form.password !== '' || form.confirmPassword !== '';
 const requiresLogin = isSelf && (emailChanging || passwordChanging);

 function update(key, value) {
  setForm(current => ({...current, [key]: value}));
  setFields(current => ({...current, [key]: undefined, ...(key === 'password' ? {confirmPassword: undefined} : {})}));
  setError('');
 }

 async function save(event) {
  event.preventDefault();
  if (pending.current) return;
  const values = {fullName: form.fullName.trim(), email: form.email.trim(), phone: form.phone.replace(/[\s().-]/g, ''), roleId: Number(form.roleId)};
  const invalid = {};
  if (values.fullName.length < 2 || values.fullName.length > 120) invalid.fullName = 'Họ và tên cần từ 2 đến 120 ký tự.';
  if (!values.email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(values.email)) invalid.email = 'Nhập email đăng nhập hợp lệ.';
  else if (values.email.length > 190) invalid.email = 'Email đăng nhập tối đa 190 ký tự.';
  if (values.phone && !/^(?:0[0-9]{9}|\+?84[0-9]{9})$/.test(values.phone)) invalid.phone = 'Nhập số điện thoại hợp lệ, ví dụ 0912345678 hoặc +84912345678.';
  if (!roles.some(role => role.id === values.roleId)) invalid.roleId = 'Vui lòng chọn một trong 7 quyền hạn hợp lệ.';
  if (!account || passwordChanging) {
   const strengthError = resetPasswordError(form.password, form.password);
   if (strengthError) invalid.password = strengthError;
   if (form.password !== form.confirmPassword) invalid.confirmPassword = 'Mật khẩu xác nhận không khớp.';
   values.password = form.password;
   values.confirmPassword = form.confirmPassword;
  }
  setFields(invalid); setError('');
  if (Object.keys(invalid).length) {formRef.current?.elements.namedItem(Object.keys(invalid)[0])?.focus(); return;}
  const controller = new AbortController(); requestRef.current = controller;
  pending.current = true; setSaving(true);
  try {
   const saved = await api(account ? '/api/admin/users/' + account.id : '/api/admin/users', {method: account ? 'PUT' : 'POST', body: JSON.stringify(values), signal: controller.signal});
   if (mounted.current && !controller.signal.aborted) onSaved(saved, Boolean(account), requiresLogin);
  } catch (cause) {
   if (!mounted.current || controller.signal.aborted) return;
   if (cause.status === 401) {onLogout(); return;}
   setError(cause.message); setFields(cause.fields || {});
   formRef.current?.elements.namedItem(Object.keys(cause.fields || {})[0])?.focus();
  } finally {
   pending.current = false;
   if (mounted.current && !controller.signal.aborted) setSaving(false);
  }
 }

 const field = (name, label, type, props = {}) => <label htmlFor={prefix + name}>{label}<input id={prefix + name} name={name} type={type} value={form[name]} onChange={event => update(name, event.target.value)} aria-invalid={Boolean(fields[name])} aria-describedby={fields[name] ? prefix + name + '-error' : type === 'password' ? prefix + 'password-hint' : undefined} {...props}/>{fields[name] && <small className="field-error" id={prefix + name + '-error'}>{fields[name]}</small>}</label>;

 return <dialog ref={dialogRef} className="accounts-dialog" aria-labelledby={titleId} onCancel={event => {event.preventDefault(); if (!pending.current) onClose();}} onClick={event => {if (event.target === dialogRef.current && !pending.current) onClose();}}>
  <div className="accounts-dialog-header"><div><span className="eyebrow">QUẢN LÝ NGƯỜI DÙNG</span><h2 id={titleId}>{account ? 'Chỉnh sửa tài khoản' : 'Tạo tài khoản mới'}</h2><p>{account ? 'Cập nhật thông tin, quyền hạn và mật khẩu khi cần.' : 'Nhập thông tin đăng nhập, mật khẩu và chọn quyền hạn phù hợp.'}</p></div><button type="button" className="icon-btn" onClick={onClose} disabled={saving} aria-label="Đóng biểu mẫu tài khoản"><X size={20}/></button></div>
  <form ref={formRef} onSubmit={save} noValidate aria-busy={saving}>
   <fieldset disabled={saving}>
    {field('fullName', 'Họ và tên *', 'text', {required: true, minLength: 2, maxLength: 120, autoComplete: 'name', 'data-initial-focus': true})}
    {field('email', 'Email đăng nhập *', 'email', {required: true, maxLength: 190, autoComplete: 'email', placeholder: 'ten@vidu.vn'})}
    {field('phone', 'Số điện thoại', 'tel', {maxLength: 20, autoComplete: 'tel', placeholder: 'Ví dụ: 0912345678'})}
    <label htmlFor={prefix + 'roleId'}>Quyền hạn *<select id={prefix + 'roleId'} name="roleId" value={form.roleId} onChange={event => update('roleId', event.target.value)} required disabled={isSelf || saving} aria-invalid={Boolean(fields.roleId)} aria-describedby={fields.roleId ? prefix + 'roleId-error' : isSelf ? prefix + 'self-role-hint' : undefined}><option value="" disabled>Chọn quyền hạn</option>{roles.map(role => <option value={role.id} key={role.id}>{role.name} ({role.code})</option>)}</select>{fields.roleId && <small className="field-error" id={prefix + 'roleId-error'}>{fields.roleId}</small>}</label>
    {isSelf && <p className="accounts-hint" id={prefix + 'self-role-hint'}>Bạn giữ quyền Quản trị viên cho tài khoản đang đăng nhập.</p>}
    <div className="accounts-password-fields">
     {field('password', account ? 'Mật khẩu mới' : 'Mật khẩu *', 'password', {required: !account || Boolean(form.confirmPassword), autoComplete: 'new-password'})}
     {field('confirmPassword', account ? 'Xác nhận mật khẩu mới' : 'Xác nhận mật khẩu *', 'password', {required: !account || Boolean(form.password), autoComplete: 'new-password'})}
    </div>
    <p className="accounts-hint" id={prefix + 'password-hint'}>{account && 'Để trống cả hai ô để giữ mật khẩu hiện tại. '}Mật khẩu có ít nhất 8 ký tự, tối đa 72 byte UTF-8; ký tự có dấu có thể chiếm nhiều byte.</p>
    {!account && <p className="accounts-info"><ShieldCheck size={16}/><span>Người dùng có thể đăng nhập bằng email và mật khẩu bạn vừa tạo.</span></p>}
    {requiresLogin && <p className="accounts-warning" role="status">Sau khi thay đổi email hoặc mật khẩu của mình, bạn cần đăng nhập lại.</p>}
    {account && !isSelf && <p className="accounts-hint">Thay đổi email, quyền hạn hoặc mật khẩu sẽ yêu cầu người dùng đăng nhập lại.</p>}
    {error && <div className="error" role="alert">{error}</div>}
    <p className="accounts-hint">* Thông tin bắt buộc.</p>
    <div className="accounts-dialog-actions"><button type="button" className="accounts-secondary" onClick={onClose}>Hủy</button><button type="submit"><Check size={16}/>{saving ? 'Đang lưu…' : account ? 'Lưu thay đổi' : 'Tạo tài khoản'}</button></div>
   </fieldset>
  </form>
 </dialog>;
}

function AccountDetails({accountId, roleLabel, onClose, onLogout}) {
 const [account, setAccount] = useState(null), [loading, setLoading] = useState(true), [error, setError] = useState(''), [reload, setReload] = useState(0);
 const requestRef = useRef(null), {dialogRef, mounted} = useAccountDialog(requestRef), titleId = useId();
 useEffect(() => {
  const controller = new AbortController(); requestRef.current = controller;
  setLoading(true); setError('');
  api('/api/admin/users/' + accountId, {signal: controller.signal}).then(data => {
   if (mounted.current && !controller.signal.aborted) setAccount(data);
  }).catch(cause => {
   if (!mounted.current || controller.signal.aborted) return;
   if (cause.status === 401) onLogout(); else setError(cause.message);
  }).finally(() => {if (mounted.current && !controller.signal.aborted) setLoading(false);});
  return () => controller.abort();
 }, [accountId, reload]);
 return <dialog ref={dialogRef} className="accounts-dialog" aria-labelledby={titleId} onCancel={event => {event.preventDefault(); onClose();}} onClick={event => {if (event.target === dialogRef.current) onClose();}}>
  <div className="accounts-dialog-header"><div><span className="eyebrow">QUẢN LÝ NGƯỜI DÙNG</span><h2 id={titleId}>Chi tiết tài khoản</h2><p>Thông tin tài khoản hiện tại trong hệ thống.</p></div><button type="button" className="icon-btn" onClick={onClose} data-initial-focus aria-label="Đóng chi tiết tài khoản"><X size={20}/></button></div>
  <div className="accounts-dialog-body" aria-busy={loading}>
   {loading ? <p className="accounts-hint" role="status">Đang tải chi tiết tài khoản…</p> : error ? <div className="error" role="alert">{error}<button type="button" className="text-button" onClick={() => setReload(value => value + 1)}><RefreshCw size={14}/>Thử lại</button></div> : account && <dl className="accounts-details">
    <div><dt>Mã tài khoản</dt><dd>#{account.id}</dd></div><div><dt>Họ và tên</dt><dd>{account.fullName}</dd></div><div><dt>Email đăng nhập</dt><dd>{account.email}</dd></div><div><dt>Số điện thoại</dt><dd>{account.phone || 'Chưa cập nhật'}</dd></div><div><dt>Quyền hạn</dt><dd>{roleLabel(account)}</dd></div><div><dt>Trạng thái</dt><dd>{statusLabel(account)}</dd></div>
   </dl>}
   <div className="accounts-dialog-actions"><button type="button" className="accounts-secondary" onClick={onClose}>Đóng</button></div>
  </div>
 </dialog>;
}

function AccountAccess({account, accountEnabled, accountLocked, onClose, onChanged, onLogout}) {
 const [updating, setUpdating] = useState(false), [error, setError] = useState('');
 const requestRef = useRef(null), pending = useRef(false), {dialogRef, mounted} = useAccountDialog(requestRef), titleId = useId(), descriptionId = useId();
 const changingLock = typeof accountLocked === 'boolean';
 const restrictingAccess = changingLock ? accountLocked : !accountEnabled;
 const actionLabel = changingLock ? (accountLocked ? 'Khóa' : 'Mở khóa') : accountEnabled ? 'Kích hoạt lại' : 'Vô hiệu hóa';
 const actionIcon = changingLock ? (accountLocked ? <Lock size={16}/> : <Unlock size={16}/>) : accountEnabled ? <UserCheck size={16}/> : <Ban size={16}/>;
 async function changeStatus() {
  if (pending.current) return;
  const controller = new AbortController(); requestRef.current = controller;
  pending.current = true; setUpdating(true); setError('');
  try {
   const saved = await api('/api/admin/users/' + account.id + (changingLock ? '/lock' : '/status'), {method: 'PATCH', body: JSON.stringify(changingLock ? {accountLocked} : {accountEnabled}), signal: controller.signal});
   if (mounted.current && !controller.signal.aborted) onChanged(saved);
  } catch (cause) {
   if (!mounted.current || controller.signal.aborted) return;
   if (cause.status === 401) {onLogout(); return;}
   setError(cause.message);
  } finally {
   pending.current = false;
   if (mounted.current && !controller.signal.aborted) setUpdating(false);
  }
 }
 return <dialog ref={dialogRef} className="accounts-dialog accounts-status-dialog" aria-labelledby={titleId} aria-describedby={descriptionId} onCancel={event => {event.preventDefault(); if (!pending.current) onClose();}} onClick={event => {if (event.target === dialogRef.current && !pending.current) onClose();}}>
  <div className="accounts-dialog-header"><div><span className="eyebrow">QUẢN LÝ NGƯỜI DÙNG</span><h2 id={titleId}>{actionLabel} tài khoản?</h2><p>Xác nhận thay đổi trạng thái truy cập của người dùng.</p></div><button type="button" className="icon-btn" onClick={onClose} disabled={updating} aria-label="Đóng xác nhận thay đổi trạng thái tài khoản"><X size={20}/></button></div>
  <div className="accounts-dialog-body" aria-busy={updating}>
   <p className="accounts-status-description" id={descriptionId}>Bạn sắp {actionLabel.toLowerCase()} tài khoản <strong>{account.fullName}</strong> (<span>{account.email}</span>). {restrictingAccess ? 'Người dùng sẽ không thể đăng nhập và các phiên đăng nhập hiện tại sẽ hết hiệu lực.' : changingLock ? 'Người dùng có thể đăng nhập lại nếu tài khoản đang được kích hoạt.' : 'Người dùng có thể đăng nhập lại nếu tài khoản không bị khóa.'} Dữ liệu và thông tin tài khoản được giữ nguyên.</p>
   {!changingLock && accountEnabled && account.accountLocked && <p className="accounts-warning" role="status">Tài khoản này vẫn đang bị khóa. Kích hoạt lại không gỡ khóa, nên người dùng vẫn chưa thể đăng nhập.</p>}
   {changingLock && !accountLocked && !account.accountEnabled && <p className="accounts-warning" role="status">Tài khoản này vẫn đang bị vô hiệu hóa. Mở khóa không kích hoạt lại tài khoản, nên người dùng vẫn chưa thể đăng nhập.</p>}
   {error && <div className="error" role="alert">{error}</div>}
   <div className="accounts-dialog-actions"><button type="button" className="accounts-secondary" onClick={onClose} disabled={updating} data-initial-focus>Hủy</button><button type="button" onClick={changeStatus} disabled={updating} className={restrictingAccess ? 'accounts-warning-button' : undefined}>{actionIcon} {updating ? 'Đang cập nhật…' : actionLabel + ' tài khoản'}</button></div>
  </div>
 </dialog>;
}

function AccountDelete({account, onClose, onDeleted, onLogout}) {
 const [deleting, setDeleting] = useState(false), [error, setError] = useState('');
 const requestRef = useRef(null), pending = useRef(false), {dialogRef, mounted} = useAccountDialog(requestRef), titleId = useId(), descriptionId = useId();
 async function remove() {
  if (pending.current) return;
  const controller = new AbortController(); requestRef.current = controller;
  pending.current = true; setDeleting(true); setError('');
  try {
   await api('/api/admin/users/' + account.id, {method: 'DELETE', signal: controller.signal});
   if (mounted.current && !controller.signal.aborted) onDeleted(account);
  } catch (cause) {
   if (!mounted.current || controller.signal.aborted) return;
   if (cause.status === 401) {onLogout(); return;}
   setError(cause.message);
  } finally {
   pending.current = false;
   if (mounted.current && !controller.signal.aborted) setDeleting(false);
  }
 }
 return <dialog ref={dialogRef} className="accounts-dialog accounts-delete-dialog" aria-labelledby={titleId} aria-describedby={descriptionId} onCancel={event => {event.preventDefault(); if (!pending.current) onClose();}} onClick={event => {if (event.target === dialogRef.current && !pending.current) onClose();}}>
  <div className="accounts-dialog-header"><div><span className="eyebrow">QUẢN LÝ NGƯỜI DÙNG</span><h2 id={titleId}>Xóa tài khoản?</h2><p>Xác nhận trước khi xóa tài khoản người dùng.</p></div><button type="button" className="icon-btn" onClick={onClose} disabled={deleting} aria-label="Đóng xác nhận xóa tài khoản"><X size={20}/></button></div>
  <div className="accounts-dialog-body" aria-busy={deleting}>
   <p className="accounts-delete-description" id={descriptionId}>Bạn sắp xóa tài khoản <strong>{account.fullName}</strong> (<span>{account.email}</span>). Người dùng sẽ không thể đăng nhập bằng tài khoản này. Thao tác này không thể hoàn tác.</p>
   <p className="accounts-hint">Tài khoản có dữ liệu nghiệp vụ liên quan sẽ được giữ lại và hệ thống sẽ thông báo lý do.</p>
   {error && <div className="error" role="alert">{error}</div>}
   <div className="accounts-dialog-actions"><button type="button" className="accounts-secondary" onClick={onClose} disabled={deleting} data-initial-focus>Hủy</button><button type="button" className="accounts-danger" onClick={remove} disabled={deleting}><Trash2 size={16}/>{deleting ? 'Đang xóa…' : 'Xóa tài khoản'}</button></div>
  </div>
 </dialog>;
}

export default function UserAccounts({user, onUserUpdate, onLogout, onAccountsChanged}) {
 const [roles, setRoles] = useState([]), [rolesLoading, setRolesLoading] = useState(true), [rolesError, setRolesError] = useState('');
 const [search, setSearch] = useState(''), [filter, setFilter] = useState({q: '', roleId: '', page: 0});
 const [result, setResult] = useState({items: [], total: 0, totalPages: 0}), [loading, setLoading] = useState(true), [error, setError] = useState('');
 const [reload, setReload] = useState(0), [roleReload, setRoleReload] = useState(0), [dialog, setDialog] = useState(null), [notice, setNotice] = useState('');
 const headingRef = useRef(null), mounted = useRef(false);

 useEffect(() => {mounted.current = true; headingRef.current?.focus(); return () => {mounted.current = false;};}, []);
 useEffect(() => {
  if (notice && !dialog) headingRef.current?.focus({preventScroll: true});
 }, [notice, dialog]);
 useEffect(() => {
  const controller = new AbortController();
  setRolesLoading(true); setRolesError('');
  api('/api/admin/roles', {signal: controller.signal}).then(list => {
   if (controller.signal.aborted) return;
   const supported = list.filter(role => Number.isInteger(role.id) && rolesById[role.id] === role.code);
   if (!supported.length) throw new Error('Chưa có quyền hạn hợp lệ để tạo hoặc chỉnh sửa tài khoản.');
   setRoles(supported);
  }).catch(cause => {if (!controller.signal.aborted) {if (cause.status === 401) onLogout(); else setRolesError(cause.message);}})
   .finally(() => {if (!controller.signal.aborted) setRolesLoading(false);});
  return () => controller.abort();
 }, [roleReload]);
 useEffect(() => {
  const timer = window.setTimeout(() => {setFilter(current => current.q === search.trim() ? current : {...current, q: search.trim(), page: 0});}, 300);
  return () => window.clearTimeout(timer);
 }, [search]);
 useEffect(() => {
  const controller = new AbortController();
  setLoading(true); setError('');
  const params = new URLSearchParams({q: filter.q, page: String(filter.page), size: String(PAGE_SIZE)});
  if (filter.roleId) params.set('roleId', filter.roleId);
  api('/api/admin/users?' + params, {signal: controller.signal}).then(data => {
   if (controller.signal.aborted) return;
   if (filter.page > 0 && filter.page >= data.totalPages) {setFilter(current => ({...current, page: Math.max(0, data.totalPages - 1)})); return;}
   setResult(data);
  }).catch(cause => {if (!controller.signal.aborted) {if (cause.status === 401) onLogout(); else setError(cause.message);}})
   .finally(() => {if (!controller.signal.aborted) setLoading(false);});
  return () => controller.abort();
 }, [filter, reload]);

 function openDialog(kind, account = null) {setNotice(''); setDialog({kind, account, ...(kind === 'status' ? {accountEnabled: !account.accountEnabled} : kind === 'lock' ? {accountLocked: !account.accountLocked} : {})});}
 function saved(account, editing, requiresLogin) {
  if (!mounted.current) return;
  onAccountsChanged?.();
  if (sameAccount(account, user)) {
   if (requiresLogin) {onLogout(); return;}
   onUserUpdate({...user, ...account});
  }
  setDialog(null);
  setNotice(editing ? 'Đã cập nhật thông tin tài khoản.' : 'Đã tạo tài khoản. Người dùng có thể đăng nhập bằng email và mật khẩu vừa tạo.');
  setError(''); setReload(value => value + 1);
 }
 function deleted(account) {
  if (!mounted.current) return;
  onAccountsChanged?.();
  setDialog(null); setNotice('Đã xóa tài khoản ' + account.fullName + '.');
  setError(''); setReload(value => value + 1);
 }
 function statusChanged(account) {
  if (!mounted.current) return;
  onAccountsChanged?.();
  if (sameAccount(account, user)) {
   if (!account.accountEnabled) {onLogout(); return;}
   onUserUpdate({...user, ...account});
  }
  setDialog(null);
  setNotice(account.accountEnabled ? 'Đã kích hoạt lại tài khoản ' + account.fullName + '.' + (account.accountLocked ? ' Tài khoản vẫn đang bị khóa và chưa thể đăng nhập.' : ' Người dùng có thể đăng nhập lại.') : 'Đã vô hiệu hóa tài khoản ' + account.fullName + '. Dữ liệu được giữ nguyên; người dùng không thể đăng nhập.');
  setError(''); setReload(value => value + 1);
 }
 function lockChanged(account) {
  if (!mounted.current) return;
  onAccountsChanged?.();
  if (sameAccount(account, user)) {
   if (account.accountLocked) {onLogout(); return;}
   onUserUpdate({...user, ...account});
  }
  setDialog(null);
  setNotice(account.accountLocked ? 'Đã khóa tài khoản ' + account.fullName + '. Dữ liệu được giữ nguyên; người dùng không thể đăng nhập.' : 'Đã mở khóa tài khoản ' + account.fullName + '.' + (account.accountEnabled ? ' Người dùng có thể đăng nhập lại.' : ' Tài khoản vẫn đang bị vô hiệu hóa và chưa thể đăng nhập.'));
  setError(''); setReload(value => value + 1);
 }

 const roleLabel = account => roles.find(role => role.id === account.roleId)?.name || dashboardRoles[account.role]?.label || account.role || 'Vai trò không hợp lệ';
 return <section className="user-accounts" aria-labelledby="accounts-heading">
  <header className="accounts-heading"><div><span className="eyebrow">QUẢN TRỊ / NGƯỜI DÙNG</span><h1 id="accounts-heading" ref={headingRef} tabIndex={-1}>Tài khoản người dùng</h1><p>Tìm kiếm, tạo mới, xem chi tiết, chỉnh sửa, quản lý trạng thái và xóa tài khoản trong hệ thống.</p></div><button type="button" onClick={() => openDialog('editor')} disabled={rolesLoading || Boolean(rolesError)}><Plus size={17}/>Tạo tài khoản</button></header>
  {notice && <div className="success accounts-notice" role="status"><Check size={17}/>{notice}</div>}
  {rolesLoading && <p className="accounts-hint" role="status">Đang tải danh sách quyền hạn…</p>}
  {rolesError && <div className="error" role="alert">{rolesError}<button type="button" className="text-button" onClick={() => setRoleReload(value => value + 1)}><RefreshCw size={14}/>Tải lại quyền hạn</button></div>}
  <div className="accounts-panel">
   <div className="accounts-toolbar"><label className="accounts-search" htmlFor="accounts-search"><span>Tìm kiếm tài khoản</span><div><Search size={17}/><input id="accounts-search" type="search" maxLength={200} value={search} onChange={event => setSearch(event.target.value)} placeholder="Họ tên, email hoặc số điện thoại" autoComplete="off"/></div></label><label className="accounts-role-filter" htmlFor="accounts-role-filter">Quyền hạn<select id="accounts-role-filter" value={filter.roleId} onChange={event => setFilter(current => ({...current, roleId: event.target.value, page: 0}))} disabled={rolesLoading || Boolean(rolesError)}><option value="">Tất cả quyền hạn</option>{roles.map(role => <option value={role.id} key={role.id}>{role.name}</option>)}</select></label><button type="button" className="accounts-secondary accounts-refresh" onClick={() => setReload(value => value + 1)} disabled={loading}><RefreshCw size={15}/>Cập nhật</button></div>
   <div className="accounts-list-heading"><h2><Users size={17}/>Danh sách tài khoản</h2><span aria-live="polite">{loading ? 'Đang tải…' : error ? 'Chưa tải được dữ liệu' : result.total.toLocaleString('vi-VN') + ' tài khoản'}</span></div>
   <div aria-busy={loading}>
    {loading ? <div className="accounts-empty" role="status"><RefreshCw size={23} className="erhms-spinning"/><p>Đang tải tài khoản…</p></div>
     : error ? <div className="accounts-empty"><p className="error" role="alert">{error}</p><button type="button" onClick={() => setReload(value => value + 1)}>Thử lại</button></div>
     : result.items.length === 0 ? <div className="accounts-empty"><Users size={28}/><h3>Không tìm thấy tài khoản</h3><p>{filter.q || filter.roleId ? 'Thử từ khóa khác hoặc chọn tất cả quyền hạn.' : 'Tạo tài khoản đầu tiên để bắt đầu quản lý người dùng.'}</p>{(filter.q || filter.roleId) && <button type="button" className="accounts-secondary" onClick={() => {setSearch(''); setFilter({q: '', roleId: '', page: 0});}}>Xóa bộ lọc</button>}</div>
     : <div className="accounts-table-wrap"><table className="accounts-table">
      <caption className="accounts-sr-only">Danh sách tài khoản người dùng và quyền hạn</caption>
      <thead><tr><th scope="col">Họ và tên</th><th scope="col">Email đăng nhập</th><th scope="col">Số điện thoại</th><th scope="col">Quyền hạn</th><th scope="col">Trạng thái</th><th scope="col">Thao tác</th></tr></thead>
      <tbody>{result.items.map(account => <tr key={account.id}>
       <th scope="row"><span>{account.fullName}</span><small>#{account.id}{sameAccount(account, user) ? ' · Bạn' : ''}</small></th>
       <td data-label="Email đăng nhập" className="accounts-email">{account.email}</td>
       <td data-label="Số điện thoại">{account.phone || 'Chưa cập nhật'}</td>
       <td data-label="Quyền hạn"><span className="accounts-role-badge">{roleLabel(account)}</span></td>
       <td data-label="Trạng thái"><span className={'accounts-status' + (account.accountLocked || !account.accountEnabled ? ' is-inactive' : '')}>{statusLabel(account)}</span></td>
       <td data-label="Thao tác"><div className="accounts-row-actions">
        <button type="button" className="accounts-edit" onClick={() => openDialog('details', account)} aria-label={'Xem chi tiết tài khoản ' + account.fullName}><Eye size={14}/>Xem chi tiết</button>
        <button type="button" className="accounts-edit" disabled={rolesLoading || Boolean(rolesError)} onClick={() => openDialog('editor', account)} aria-label={'Chỉnh sửa tài khoản ' + account.fullName}><Pencil size={14}/>Chỉnh sửa</button>
        <button type="button" className={'accounts-edit' + (account.accountEnabled ? ' accounts-disable' : '')} disabled={sameAccount(account, user) && account.accountEnabled} title={sameAccount(account, user) && account.accountEnabled ? 'Không thể vô hiệu hóa tài khoản đang đăng nhập' : undefined} aria-describedby={sameAccount(account, user) && account.accountEnabled ? 'account-access-hint-' + account.id : undefined} onClick={() => openDialog('status', account)} aria-label={(account.accountEnabled ? 'Vô hiệu hóa tài khoản ' : 'Kích hoạt lại tài khoản ') + account.fullName}>{account.accountEnabled ? <Ban size={14}/> : <UserCheck size={14}/>} {account.accountEnabled ? 'Vô hiệu hóa' : 'Kích hoạt lại'}</button>
        <button type="button" className={'accounts-edit' + (!account.accountLocked ? ' accounts-lock' : '')} disabled={sameAccount(account, user) && !account.accountLocked} title={sameAccount(account, user) && !account.accountLocked ? 'Không thể khóa tài khoản đang đăng nhập' : undefined} aria-describedby={sameAccount(account, user) && !account.accountLocked ? 'account-access-hint-' + account.id : undefined} onClick={() => openDialog('lock', account)} aria-label={(account.accountLocked ? 'Mở khóa tài khoản ' : 'Khóa tài khoản ') + account.fullName}>{account.accountLocked ? <Unlock size={14}/> : <Lock size={14}/>} {account.accountLocked ? 'Mở khóa' : 'Khóa'}</button>
        <button type="button" className="accounts-edit accounts-delete" disabled={sameAccount(account, user)} title={sameAccount(account, user) ? 'Không thể xóa tài khoản đang đăng nhập' : undefined} onClick={() => openDialog('delete', account)} aria-label={'Xóa tài khoản ' + account.fullName}><Trash2 size={14}/>Xóa</button>
        {sameAccount(account, user) && <small className="accounts-self-status-hint" id={'account-access-hint-' + account.id}>Không thể vô hiệu hóa hoặc khóa tài khoản đang đăng nhập.</small>}
       </div></td>
      </tr>)}</tbody>
     </table></div>}
   </div>
   {!loading && !error && result.total > 0 && <footer className="accounts-pagination"><span>{filter.page * PAGE_SIZE + 1}–{Math.min((filter.page + 1) * PAGE_SIZE, result.total)} trong {result.total.toLocaleString('vi-VN')} tài khoản</span><div><button type="button" className="accounts-secondary" disabled={filter.page === 0} onClick={() => setFilter(current => ({...current, page: current.page - 1}))} aria-label="Trang tài khoản trước"><ChevronLeft size={16}/></button><span>Trang {filter.page + 1} / {result.totalPages}</span><button type="button" className="accounts-secondary" disabled={filter.page + 1 >= result.totalPages} onClick={() => setFilter(current => ({...current, page: current.page + 1}))} aria-label="Trang tài khoản tiếp theo"><ChevronRight size={16}/></button></div></footer>}
  </div>
  {dialog?.kind === 'editor' && <AccountEditor account={dialog.account} roles={roles} user={user} onClose={() => setDialog(null)} onSaved={saved} onLogout={onLogout}/>}
  {dialog?.kind === 'details' && <AccountDetails accountId={dialog.account.id} roleLabel={roleLabel} onClose={() => setDialog(null)} onLogout={onLogout}/>}
  {dialog?.kind === 'delete' && <AccountDelete account={dialog.account} onClose={() => setDialog(null)} onDeleted={deleted} onLogout={onLogout}/>}
  {dialog?.kind === 'status' && <AccountAccess account={dialog.account} accountEnabled={dialog.accountEnabled} onClose={() => setDialog(null)} onChanged={statusChanged} onLogout={onLogout}/>}
  {dialog?.kind === 'lock' && <AccountAccess account={dialog.account} accountLocked={dialog.accountLocked} onClose={() => setDialog(null)} onChanged={lockChanged} onLogout={onLogout}/>}
 </section>;
}
