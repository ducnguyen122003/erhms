import {useEffect, useRef, useState} from 'react';
import {ArrowLeft, ArrowRight, CheckCircle2, Mail, RefreshCw} from 'lucide-react';
import {api, login} from './api';
import {Brand} from './RoleWorkspace';
import {resetPasswordError} from './authValidation';
import './auth.css';

const resetApi = '/api/auth/password-reset';
const genericNotice = 'Nếu email đã đăng ký và tài khoản đủ điều kiện, mã xác nhận gồm 6 số sẽ được gửi đến email của bạn. Vui lòng kiểm tra cả thư rác.';
const titles = {login: 'Chào mừng trở lại', email: 'Quên mật khẩu?', code: 'Kiểm tra email của bạn', password: 'Đặt mật khẩu mới'};
const descriptions = {
 login: 'Đăng nhập để mở dashboard theo vai trò của bạn.',
 email: 'Nhập email đã đăng ký để nhận mã xác nhận gồm 6 số.',
 code: 'Nhập mã xác nhận được gửi đến email của bạn. Mã có hiệu lực trong 10 phút.',
 password: 'Tạo mật khẩu mới cho tài khoản đăng nhập của bạn.'
};
const secondsLeft = deadline => Math.max(0, Math.ceil((deadline - Date.now()) / 1000));
const duration = (value, fallback) => Number.isFinite(value) && value > 0 ? value : fallback;

export default function Login({onLogin}) {
 const [stage, setStage] = useState('login');
 const [email, setEmail] = useState(''), [password, setPassword] = useState('');
 const [code, setCode] = useState(''), [newPassword, setNewPassword] = useState(''), [confirmation, setConfirmation] = useState('');
 const [error, setError] = useState(''), [notice, setNotice] = useState(''), [busy, setBusy] = useState(false);
 const [retryAt, setRetryAt] = useState(0), [verifyRetryAt, setVerifyRetryAt] = useState(0), [codeExpiresAt, setCodeExpiresAt] = useState(0), [tokenExpiresAt, setTokenExpiresAt] = useState(0);
 const [now, setNow] = useState(Date.now());
 const requestVersion = useRef(0), busyRef = useRef(false), mounted = useRef(true), resetToken = useRef(null);
 const retrySeconds = Math.max(0, Math.ceil((retryAt - now) / 1000));
 const verifyRetrySeconds = Math.max(0, Math.ceil((verifyRetryAt - now) / 1000));
 const codeExpired = stage === 'code' && codeExpiresAt > 0 && now >= codeExpiresAt;

 useEffect(() => {
  mounted.current = true;
  return () => {mounted.current = false; requestVersion.current++; resetToken.current = null;};
 }, []);
 useEffect(() => {
  if (stage === 'login') return;
  const timer = window.setInterval(() => setNow(Date.now()), 1000);
  return () => window.clearInterval(timer);
 }, [stage]);
 useEffect(() => {
  if (codeExpired && !busy) {setCode(''); setError('Mã xác nhận đã hết hạn. Vui lòng gửi lại mã.'); setNotice('');}
 }, [codeExpired, busy]);
 useEffect(() => {
  if (stage === 'password' && !busy && tokenExpiresAt > 0 && now >= tokenExpiresAt) {
   moveTo('email');
   setError('Phiên đặt lại mật khẩu đã hết hạn. Vui lòng yêu cầu mã xác nhận mới.');
  }
 }, [stage, tokenExpiresAt, now, busy]);

 function clearReset() {
  resetToken.current = null; setTokenExpiresAt(0); setCodeExpiresAt(0);
  setCode(''); setNewPassword(''); setConfirmation(''); setVerifyRetryAt(0);
 }
 function moveTo(next) {
  requestVersion.current++; busyRef.current = false; setBusy(false);
  clearReset(); setPassword(''); setError(''); setNotice(''); setStage(next); setNow(Date.now());
 }
 function begin() {
  if (busyRef.current) return null;
  busyRef.current = true; setBusy(true); setError('');
  return ++requestVersion.current;
 }
 const current = version => mounted.current && requestVersion.current === version;
 function finish(version) {if (current(version)) {busyRef.current = false; setBusy(false);}}
 function throttle(cause, target = 'request') {
  if (cause.status === 429) {
   const deadline = Date.now() + duration(cause.retryAfterSeconds, 60) * 1000;
   if (target === 'verify') setVerifyRetryAt(deadline); else setRetryAt(deadline);
   setNow(Date.now());
  }
 }

 async function submitLogin(event) {
  event.preventDefault();
  const version = begin(); if (version === null) return;
  try {const user = await login(email.trim(), password); if (current(version)) onLogin(user);}
  catch (cause) {if (current(version)) {setNotice(''); setError(cause.message);}}
  finally {finish(version);}
 }
 async function requestCode(event) {
  event?.preventDefault();
  if (secondsLeft(retryAt) > 0) return;
  const version = begin(); if (version === null) return;
  try {
   const result = await api(resetApi + '/request', {method: 'POST', body: JSON.stringify({email: email.trim()})});
   if (!current(version)) return;
   clearReset(); setEmail(email.trim()); setCodeExpiresAt(Date.now() + duration(result.expiresInSeconds, 600) * 1000);
   setRetryAt(Date.now() + duration(result.retryAfterSeconds, 60) * 1000); setNow(Date.now());
   setNotice(result.message || genericNotice); setStage('code');
  } catch (cause) {
   if (current(version)) {throttle(cause); setNotice(''); setError(cause.message);}
  } finally {finish(version);}
 }
 async function verifyCode(event) {
  event.preventDefault();
  if (secondsLeft(verifyRetryAt) > 0) return;
  if (!/^[0-9]{6}$/.test(code)) {setError('Vui lòng nhập đầy đủ 6 chữ số của mã xác nhận.'); return;}
  if (codeExpiresAt <= Date.now()) {setError('Mã xác nhận đã hết hạn. Vui lòng gửi lại mã.'); return;}
  const version = begin(); if (version === null) return;
  try {
   const result = await api(resetApi + '/verify', {method: 'POST', body: JSON.stringify({email: email.trim(), code})});
   if (!current(version)) return;
   if (!result.resetToken) throw new Error('Chưa xác nhận được mã. Vui lòng thử lại.');
   resetToken.current = result.resetToken; setTokenExpiresAt(Date.now() + duration(result.expiresInSeconds, 300) * 1000);
   setCode(''); setNotice(''); setNewPassword(''); setConfirmation(''); setNow(Date.now()); setStage('password');
  } catch (cause) {
   if (current(version)) {
    if (cause.code === 'RESET_ATTEMPTS_EXCEEDED') {moveTo('email'); throttle(cause);}
    else {
     throttle(cause, 'verify');
     if (cause.code === 'RESET_CODE_EXPIRED') setCodeExpiresAt(Date.now());
    }
    setNotice(''); setError(cause.message);
   }
  } finally {finish(version);}
 }
 async function completeReset(event) {
  event.preventDefault();
  const validation = resetPasswordError(newPassword, confirmation);
  if (validation) {setError(validation); return;}
  if (!resetToken.current || tokenExpiresAt <= Date.now()) {
   moveTo('email'); setError('Phiên đặt lại mật khẩu đã hết hạn. Vui lòng yêu cầu mã xác nhận mới.'); return;
  }
  const version = begin(); if (version === null) return;
  try {
   await api(resetApi + '/complete', {method: 'POST', body: JSON.stringify({email: email.trim(), resetToken: resetToken.current, newPassword, confirmPassword: confirmation})});
   if (!current(version)) return;
   moveTo('login'); setRetryAt(0); setNotice('Đổi mật khẩu thành công. Bạn có thể đăng nhập bằng mật khẩu mới.');
  } catch (cause) {
   if (!current(version)) return;
   if (['RESET_TOKEN_INVALID', 'RESET_TOKEN_EXPIRED'].includes(cause.code) || cause.fields?.resetToken) moveTo('email');
   throttle(cause); setError(cause.message);
  } finally {finish(version);}
 }
 function changeEmail(value) {
  setEmail(value); clearReset(); setError(''); setNotice('');
 }

 return <div className="login-layout">
  <section className="login-story">
   <Brand/>
   <div><span className="eyebrow light">KẾT NỐI CON NGƯỜI · PHÁT TRIỂN KỸ NĂNG</span><h1>Mỗi hành trình. <br/>Một cơ hội. <br/><em>Cùng phát triển.</em></h1><p>Không gian làm việc chung cho tuyển sinh, đào tạo, tài chính và quản lý thiết bị của trung tâm.</p><div className="story-path"><span>01 / Tuyển sinh</span><ArrowRight/><span>02 / Đào tạo</span><ArrowRight/><span>03 / Nghề nghiệp</span></div></div>
   <small>ERHMS / Hệ thống quản lý đào tạo</small>
  </section>
  <main className="login-form"><div className="login-card">
   <span className="eyebrow">KHÔNG GIAN LÀM VIỆC</span>
   <h2>{titles[stage]}</h2><p className="muted">{descriptions[stage]}</p>
   {stage !== 'login' && <ol className="auth-steps" aria-label="Tiến trình đặt lại mật khẩu">{[['email', 'Email'], ['code', 'Xác nhận'], ['password', 'Mật khẩu']].map(([step, label], index) => <li key={step} aria-current={stage === step ? 'step' : undefined}><span>{index + 1}</span>{label}</li>)}</ol>}
   {['code', 'password'].includes(stage) && <div className="auth-recipient"><Mail size={17} aria-hidden="true"/><span>{email}</span><button type="button" className="auth-link" onClick={() => moveTo('email')}>Đổi email</button></div>}
   {notice && <p className={stage === 'login' ? 'success auth-notice' : 'auth-notice'} role="status">{stage === 'login' && <CheckCircle2 size={18} aria-hidden="true"/>}{notice}</p>}
   <form key={stage} onSubmit={stage === 'login' ? submitLogin : stage === 'email' ? requestCode : stage === 'code' ? verifyCode : completeReset} aria-busy={busy}>
    {['login', 'email'].includes(stage) && <label htmlFor="login-email">Email{stage === 'email' ? ' đã đăng ký' : ''}<input id="login-email" type="email" value={email} onChange={event => changeEmail(event.target.value)} autoComplete="username" placeholder="Nhập email của bạn" maxLength={190} autoFocus required disabled={busy}/></label>}
    {stage === 'login' && <><label htmlFor="login-password">Mật khẩu<input id="login-password" type="password" value={password} onChange={event => setPassword(event.target.value)} autoComplete="current-password" placeholder="Nhập mật khẩu của bạn" required disabled={busy}/></label><div className="auth-forgot"><button type="button" className="auth-link" disabled={busy} onClick={() => moveTo('email')}>Quên mật khẩu?</button></div></>}
    {stage === 'code' && <label htmlFor="reset-code">Mã xác nhận 6 số<input id="reset-code" className="auth-code" type="text" inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" minLength={6} maxLength={6} placeholder="000000" value={code} onChange={event => {setCode(event.target.value.replace(/[^0-9]/g, '').slice(0, 6)); setError('');}} aria-describedby="reset-code-hint" autoFocus required disabled={busy || codeExpired}/><span className="auth-field-hint" id="reset-code-hint">{codeExpired ? 'Mã đã hết hạn. Chọn gửi lại mã để tiếp tục.' : 'Mã có 6 chữ số, bao gồm cả số 0 ở đầu nếu có.'}</span></label>}
    {stage === 'password' && <><label htmlFor="reset-password">Mật khẩu mới<input id="reset-password" type="password" value={newPassword} onChange={event => setNewPassword(event.target.value)} autoComplete="new-password" aria-describedby="reset-password-hint" autoFocus required disabled={busy}/><span className="auth-field-hint" id="reset-password-hint">Ít nhất 8 ký tự, tối đa 72 byte.</span></label><label htmlFor="reset-confirmation">Xác nhận lại mật khẩu<input id="reset-confirmation" type="password" value={confirmation} onChange={event => setConfirmation(event.target.value)} autoComplete="new-password" required disabled={busy}/></label></>}
    {error && <p role="alert" className="error">{error}</p>}
    <button type="submit" className="full" disabled={busy || (stage === 'email' && retrySeconds > 0) || (stage === 'code' && (code.length !== 6 || codeExpired || verifyRetrySeconds > 0))}>{busy ? 'Đang xử lý…' : stage === 'login' ? 'Đăng nhập' : stage === 'email' ? retrySeconds > 0 ? `Gửi lại sau ${retrySeconds}s` : 'Gửi mã xác nhận' : stage === 'code' ? verifyRetrySeconds > 0 ? `Thử lại sau ${verifyRetrySeconds}s` : 'Xác nhận mã' : 'Lưu mật khẩu mới'}<ArrowRight size={18} aria-hidden="true"/></button>
   </form>
   {stage === 'code' && <div className="auth-resend"><span>Chưa nhận được mã?</span><button type="button" className="auth-link" onClick={requestCode} disabled={busy || retrySeconds > 0}><RefreshCw size={13} aria-hidden="true"/>{retrySeconds > 0 ? `Gửi lại sau ${retrySeconds}s` : 'Gửi lại mã'}</button></div>}
   {stage === 'email' && <p className="auth-help">Tài khoản mới được quản trị viên tạo cũng có thể dùng bước này để thiết lập mật khẩu lần đầu.</p>}
   {stage !== 'login' && <button type="button" className="auth-link auth-back" onClick={() => moveTo('login')}><ArrowLeft size={15} aria-hidden="true"/>Quay lại đăng nhập</button>}
  </div></main>
 </div>;
}
