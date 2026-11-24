import {useEffect,useMemo,useRef,useState} from 'react';
import {Check,Copy,Download,RefreshCw,Upload} from 'lucide-react';
import QRCode from 'qrcode';
import {api} from '../api';
import {AdminLoading,AdminMessage,formatTime} from '../admin/AdminUI';
import {display} from './definitions';
import {useUnsavedChanges} from './ScreenForm';
import {hasPendingPayment,paymentAmount,paymentApi,paymentKinds,paymentToday,selectedPayment,studentPaymentPayload,submissionStatuses,validateStudentPayment} from './studentPayments';
import './student-payments.css';

function TransferForm({contractId,guide,user,onSaved,onLogout,onBusy}){
 const options=guide.paymentOptions||[];
 const [values,setValues]=useState(()=>({selected:Object.fromEntries(['TUITION','DEPOSIT'].map(kind=>[kind,options.some(option=>option.kind===kind&&Number(option.remaining)>0)&&!hasPendingPayment(guide.submissions,kind)])),amounts:Object.fromEntries(options.filter(option=>option.kind!=='COMBINED').map(option=>[option.kind,option.remaining??''])),payerName:user.fullName||'',paidOn:'',declaredBankReference:'',note:''}));
 const [file,setFile]=useState(null),[fileKey,setFileKey]=useState(0),[uploaded,setUploaded]=useState(null),[busy,setBusy]=useState(false),[error,setError]=useState(''),[errors,setErrors]=useState({}),[copyNotice,setCopyNotice]=useState('');
 const [qr,setQr]=useState(null),[qrLoading,setQrLoading]=useState(false),[qrError,setQrError]=useState('');
 const mounted=useRef(false),pending=useRef(false),request=useRef(null),dirty=useUnsavedChanges();
 const option=useMemo(()=>selectedPayment(values,options),[values.selected,values.amounts,options]);
 const amount=paymentAmount(option.amount),remaining=Number(option.remaining||0),waiting=Boolean(option.kind)&&hasPendingPayment(guide.submissions,option.kind);
 const validAmounts=option.kinds.length>0&&option.kinds.every(kind=>{const value=paymentAmount(values.amounts[kind]);return value!==null&&value<=Number(options.find(item=>item.kind===kind)?.remaining||0);});
 const canSubmit=guide.canSubmit&&remaining>0&&!waiting&&option.kinds.length>0;
 useEffect(()=>{mounted.current=true;return()=>{mounted.current=false;request.current?.abort();};},[]);
 useEffect(()=>{onBusy?.(busy);return()=>onBusy?.(false);},[busy]);
 useEffect(()=>{
  const controller=new AbortController();let live=true;setQr(null);setQrError('');setQrLoading(false);
  if(!option.kind||!validAmounts||amount===null||amount>remaining||!guide.canSubmit||waiting)return()=>controller.abort();
  setQrLoading(true);
  const timer=setTimeout(async()=>{
   try{
    const payload=amount===remaining&&option.qrPayload?option.qrPayload:(await api(paymentApi+'/contracts/'+encodeURIComponent(contractId)+'/qr?'+new URLSearchParams({kind:option.kind,amount}),{signal:controller.signal})).qrPayload;
    const url=await QRCode.toDataURL(payload,{width:264,margin:2,errorCorrectionLevel:'M'});
    if(live&&!controller.signal.aborted)setQr(url);
   }catch(cause){if(live&&!controller.signal.aborted){if(cause.status===401)onLogout?.();else setQrError(cause.message||'Chưa tạo được mã QR. Bạn vẫn có thể dùng số tài khoản và nội dung bên cạnh để chuyển khoản.');}}
   finally{if(live&&!controller.signal.aborted)setQrLoading(false);}
  },amount===remaining?0:300);
  return()=>{live=false;clearTimeout(timer);controller.abort();};
 },[contractId,amount,remaining,option,guide.canSubmit,waiting,validAmounts]);
 function change(key,value){dirty.current=true;setErrors(current=>({...current,[key]:undefined}));setValues(current=>({...current,[key]:value}));}
 function toggleKind(kind){dirty.current=true;setValues(current=>({...current,selected:{...current.selected,[kind]:!current.selected[kind]}}));setFile(null);setUploaded(null);setFileKey(value=>value+1);setErrors({});setError('');}
 function changeAmount(kind,value){dirty.current=true;setErrors(current=>({...current,amount:undefined,[kind==='TUITION'?'tuitionAmount':'depositAmount']:undefined}));setValues(current=>({...current,amounts:{...current.amounts,[kind]:value}}));}
 async function copy(value,label){try{await navigator.clipboard.writeText(String(value));setCopyNotice('Đã sao chép '+label+'.');}catch{setCopyNotice('Bạn có thể chọn nội dung trong ô và sao chép thủ công.');}}
 async function submit(event){
  event.preventDefault();if(pending.current||!canSubmit)return;
  const effective={...values,kind:option.kind,amount:option.amount,tuitionAmount:values.amounts.TUITION,depositAmount:values.amounts.DEPOSIT,transferMemo:option.transferMemo},invalid=validateStudentPayment(effective,option,file,{submissions:guide.submissions});
  setErrors(invalid);setError('');if(Object.keys(invalid).length){setError('Kiểm tra các thông tin cần bổ sung trước khi gửi bill.');return;}
  pending.current=true;setBusy(true);const controller=new AbortController();request.current=controller;
  try{
   let receipt=uploaded;
   if(!receipt){const form=new FormData();form.append('file',file);form.append('screen','TuitionDetail_Student');receipt=await api('/api/workspace/files',{method:'POST',body:form,signal:controller.signal});if(mounted.current&&!controller.signal.aborted)setUploaded(receipt);}
   const result=await api(paymentApi+'/contracts/'+encodeURIComponent(contractId)+'/submissions',{method:'POST',body:JSON.stringify(studentPaymentPayload(effective,option,receipt.id)),signal:controller.signal});
   if(mounted.current&&!controller.signal.aborted){dirty.current=false;onSaved(result);}
  }catch(cause){if(mounted.current&&!controller.signal.aborted){if(cause.status===401)onLogout?.();else{setError(cause.message);setErrors(cause.fields||{});}}}
  finally{pending.current=false;if(mounted.current&&!controller.signal.aborted)setBusy(false);}
 }
 const bank=guide.bank||{},fieldError=key=>errors[key]&&<small className="field-error">{errors[key]}</small>;
 return <form className="student-payment-form" onSubmit={submit} aria-busy={busy}>
  <div className="student-payment-bank-grid"><section className="student-payment-bank"><h3>Thông tin nhận chuyển khoản</h3><p className="admin-muted">Chọn học phí, tiền cọc hoặc cả hai để chuyển khoản một lần. QR tự điền tổng số tiền và nội dung chuyển khoản.</p>
   <dl><div><dt>Ngân hàng</dt><dd>{bank.bankName}</dd></div><div><dt>Chủ tài khoản</dt><dd>{bank.accountHolder}</dd></div></dl>
   <label className="admin-field">Số tài khoản<div className="payment-copy-field"><input value={bank.accountNumber||''} readOnly onFocus={event=>event.target.select()} aria-label="Số tài khoản nhận tiền"/><button type="button" className="admin-secondary" onClick={()=>copy(bank.accountNumber,'số tài khoản')} aria-label="Sao chép số tài khoản"><Copy size={15}/></button></div></label>
   <fieldset className="payment-charge-selection" disabled={busy||!guide.canSubmit}><legend>Chọn khoản nộp *</legend>{options.filter(item=>item.kind!=='COMBINED').map(item=>{const pendingKind=hasPendingPayment(guide.submissions,item.kind),paid=Number(item.remaining)<=0,key=item.kind==='TUITION'?'tuitionAmount':'depositAmount';return <div className={'payment-charge'+(values.selected[item.kind]?' is-selected':'')} key={item.kind}><label className="payment-charge-choice"><input type="checkbox" checked={Boolean(values.selected[item.kind])} disabled={paid||pendingKind} onChange={()=>toggleKind(item.kind)}/><span><strong>{item.label||paymentKinds[item.kind]}</strong><small>{pendingKind?'Đang chờ kế toán đối chiếu bill':paid?'Đã xác nhận đủ':'Còn phải nộp: '+display(item.remaining,'amount')}</small></span></label><label className="admin-field">Số tiền nộp {item.kind==='TUITION'?'học phí':'cọc'} (đ)<input type="number" inputMode="numeric" min="1" step="1" max={Number(item.remaining)||undefined} value={values.amounts[item.kind]??''} disabled={!values.selected[item.kind]||paid||pendingKind} onChange={event=>changeAmount(item.kind,event.target.value)} required={Boolean(values.selected[item.kind])} aria-invalid={Boolean(errors[key]||errors.amount)}/>{fieldError(key)}</label></div>;})}{fieldError('kind')}</fieldset>
   <div className="payment-transfer-total" aria-live="polite"><span>Tổng số tiền chuyển khoản</span><strong>{display(option.amount,'amount')}</strong></div>{fieldError('amount')}
   <label className="admin-field">Nội dung chuyển khoản bắt buộc<div className="payment-copy-field"><input value={option.transferMemo||options[0]?.transferMemo||''} readOnly onFocus={event=>event.target.select()} aria-label="Nội dung chuyển khoản bắt buộc"/><button type="button" className="admin-secondary" onClick={()=>copy(option.transferMemo||options[0]?.transferMemo||'','nội dung chuyển khoản')} aria-label="Sao chép nội dung chuyển khoản"><Copy size={15}/></button></div><small>Họ tên không dấu và mã học viên. Tên dài được rút gọn; vui lòng sao chép đúng nội dung hệ thống hiển thị.</small>{fieldError('transferMemo')}</label>
   {copyNotice&&<p className="payment-copy-notice" role="status">{copyNotice}</p>}
  </section><aside className="student-payment-qr" aria-label="Mã QR chuyển khoản"><h3>QR chuyển khoản</h3>{waiting?<p role="status">Đang chờ kế toán đối chiếu bill của khoản này. Vui lòng chờ kết quả trước khi thực hiện thêm chuyển khoản.</p>:qrLoading?<p role="status">Đang tạo mã QR…</p>:qr?<><img src={qr} alt={'QR chuyển khoản '+(paymentKinds[option.kind]||'')+' cho hợp đồng '+guide.contract.code}/><strong>{display(amount,'amount')}</strong><span>{option.transferMemo}</span><a className="screen-file-link" href={qr} download={'qr-'+guide.contract.code+'-'+option.kind.toLowerCase()+'.png'}><Download size={14}/>Tải mã QR</a></>:<p className="admin-muted">Chọn khoản thu và nhập số tiền hợp lệ để hiển thị QR.</p>}{qrError&&<p className="field-error" role="alert">{qrError}</p>}</aside></div>
  {waiting?<p className="student-payment-waiting" role="status">Khoản {paymentKinds[option.kind]?.toLowerCase()} này đã có bill chờ kế toán đối chiếu. Bill đang chờ chưa được tính vào số tiền đã nộp.</p>:!guide.canSubmit?<p className="admin-message">Hợp đồng hiện chưa cho phép gửi bill mới. Bạn vẫn có thể xem lịch sử và liên hệ trung tâm để được hỗ trợ.</p>:options.every(item=>Number(item.remaining)<=0)?<p className="admin-message"><Check size={15}/>Học phí và tiền cọc đã được kế toán xác nhận đủ.</p>:!option.kinds.length?<p className="admin-message">Chọn khoản còn phải nộp để tạo QR và gửi bill. Khoản đang chờ kế toán đối chiếu sẽ được mở lại sau khi có kết quả.</p>:null}
  {canSubmit&&<section className="student-payment-upload"><h3>Đã chuyển khoản? Gửi bill cho kế toán</h3><p className="admin-muted">Tải bill PDF, PNG hoặc JPEG tối đa 2 MB. Số tiền chỉ được ghi nhận sau khi kế toán đối chiếu sao kê.</p><AdminMessage error={error}/><fieldset disabled={busy}><div className="admin-field-grid">
   <label className="admin-field">Tên người chuyển khoản *<input value={values.payerName} onChange={event=>change('payerName',event.target.value)} maxLength={160} required autoComplete="name" aria-invalid={Boolean(errors.payerName)}/>{fieldError('payerName')}</label>
   <label className="admin-field">Ngày chuyển khoản *<input type="date" value={values.paidOn} onChange={event=>change('paidOn',event.target.value)} max={paymentToday()} required aria-invalid={Boolean(errors.paidOn)}/>{fieldError('paidOn')}</label>
   <label className="admin-field screen-field-wide">Bill chuyển khoản *<input key={fileKey} type="file" accept="application/pdf,image/png,image/jpeg,.pdf,.png,.jpg,.jpeg" required onChange={event=>{dirty.current=true;setFile(event.target.files?.[0]||null);setUploaded(null);setErrors(current=>({...current,receiptFileId:undefined}));}} aria-invalid={Boolean(errors.receiptFileId||errors.evidence)}/>{file&&<small>{file.name}</small>}{fieldError('receiptFileId')}{fieldError('evidence')}</label>
   <label className="admin-field">Mã giao dịch ghi trên bill (nếu có)<input value={values.declaredBankReference} onChange={event=>change('declaredBankReference',event.target.value)} maxLength={120}/>{fieldError('declaredBankReference')}</label>
   <label className="admin-field screen-field-wide">Ghi chú (nếu có)<textarea rows={2} value={values.note} onChange={event=>change('note',event.target.value)} maxLength={1000}/>{fieldError('note')}</label>
  </div></fieldset><button type="submit" disabled={busy||!file||!values.paidOn||!validAmounts||amount===null||amount>remaining}><Upload size={15}/>{busy?'Đang gửi bill…':'Gửi bill để kế toán đối chiếu'}</button></section>}
 </form>;
}

export default function StudentContractPayment({contractId,user,onLogout,onUpdated}){
 const [data,setData]=useState(null),[loading,setLoading]=useState(true),[error,setError]=useState(''),[notice,setNotice]=useState(''),[revision,setRevision]=useState(0),[transferBusy,setTransferBusy]=useState(false);
 useEffect(()=>{const controller=new AbortController();setLoading(true);setError('');api(paymentApi+'/contracts/'+encodeURIComponent(contractId),{signal:controller.signal}).then(result=>{if(!controller.signal.aborted)setData(result);}).catch(cause=>{if(!controller.signal.aborted){if(cause.status===401)onLogout?.();else setError(cause.message);}}).finally(()=>{if(!controller.signal.aborted)setLoading(false);});return()=>controller.abort();},[contractId,revision]);
 function refresh(){if(transferBusy||!window.dispatchEvent(new Event('erhms-before-navigate',{cancelable:true})))return;if(onUpdated)onUpdated();else setRevision(value=>value+1);}
 function saved(result){const message=result.notice||'Đã gửi bill. Vui lòng chờ kế toán đối chiếu sao kê.';if(onUpdated)onUpdated(message);else{setNotice(message);setRevision(value=>value+1);}}
 const contract=data?.contract;
 const enrolledClass=[contract?.enrolledClassCode,contract?.enrolledClassName].filter(Boolean).join(' · ');
 return <section className="admin-card student-payment-panel" aria-label="Nộp tiền theo hợp đồng"><header className="student-payment-heading"><div><h2>Nộp tiền theo hợp đồng</h2><p>Chuyển khoản đúng nội dung, gửi bill và theo dõi kết quả xác nhận của kế toán.</p></div><button type="button" className="admin-secondary" onClick={refresh} disabled={loading||transferBusy}><RefreshCw size={15}/>Cập nhật trạng thái</button></header><AdminMessage notice={notice}/>
  {loading?<AdminLoading/>:error?<AdminLoading error={error} onRetry={()=>setRevision(value=>value+1)}/>:contract?<>
   <div className="student-payment-identity"><strong>{contract.studentName}</strong><span>Mã học viên: {contract.studentCode||user.studentCode}</span></div>
   <div className="student-payment-totals"><div><span>Học phí hợp đồng</span><strong>{display(contract.tuitionDue,'amount')}</strong></div><div><span>Học phí đã xác nhận</span><strong>{display(contract.tuitionPaid,'amount')}</strong></div><div><span>Học phí còn phải nộp</span><strong>{display(contract.tuitionRemaining,'amount')}</strong></div>{Number(contract.depositDue)>0&&<><div><span>Cọc theo hợp đồng</span><strong>{display(contract.depositDue,'amount')}</strong></div><div><span>Cọc đã xác nhận</span><strong>{display(contract.depositPaid,'amount')}</strong></div><div><span>Cọc còn phải nộp</span><strong>{display(contract.depositRemaining,'amount')}</strong></div></>}</div>
   {enrolledClass&&<p className="student-payment-eligibility">Bạn đã được xếp vào lớp <strong>{enrolledClass}</strong>.</p>}
   {!enrolledClass&&Number(contract.tuitionRemaining)===0&&contract.status==='ACTIVE'&&<p className="student-payment-eligibility">Học phí đã được xác nhận đủ. Quản lý học viên sẽ kiểm tra hồ sơ xếp lớp.</p>}
   {!enrolledClass&&Number(contract.tuitionRemaining)>0&&data.canSubmit&&<p className="student-payment-eligibility">{contract.classId?'Hệ thống xếp bạn vào lớp ghi trên hợp đồng khi kế toán xác nhận đủ học phí.':'Quản lý học viên sẽ xếp lớp sau khi kế toán xác nhận đủ học phí.'}</p>}
   {(data.paymentOptions||[]).length>0&&<TransferForm key={contractId+':'+revision} contractId={contractId} guide={data} user={user} onSaved={saved} onLogout={onLogout} onBusy={setTransferBusy}/>}
   <section className="student-payment-history"><h3>Bill đã gửi và kết quả đối chiếu</h3>{data.submissions?.length?<div className="admin-table-scroll"><table className="admin-table"><thead><tr><th>Khoản thu</th><th>Số tiền</th><th>Ngày gửi</th><th>Trạng thái</th><th>Bill và ý kiến kế toán</th></tr></thead><tbody>{data.submissions.map(item=><tr key={item.id}><td>{paymentKinds[item.kind]}{item.kind==='COMBINED'&&<><small>Học phí: {display(item.tuitionAmount,'amount')}</small><small>Tiền cọc: {display(item.depositAmount,'amount')}</small></>}</td><td>{display(item.amount,'amount')}</td><td>{formatTime(item.createdAt)}</td><td><span className={'admin-badge'+(item.status==='CONFIRMED'?'':' is-warning')}>{submissionStatuses[item.status]||item.status}</span></td><td>{item.receiptFileId&&<a className="screen-file-link" href={'/api/workspace/files/'+encodeURIComponent(item.receiptFileId)} download>{item.filename||'Tải bill đã gửi'}</a>}{item.reviewNote&&<small>{item.reviewNote}</small>}</td></tr>)}</tbody></table></div>:<p className="admin-muted">Bạn chưa gửi bill chuyển khoản cho hợp đồng này.</p>}</section>
  </>:null}
 </section>;
}
