import {useEffect,useRef,useState} from 'react';
import {Check,Copy,RefreshCw,Search} from 'lucide-react';
import {api} from '../api';
import {AdminDialog,AdminLoading,AdminMessage,AdminPagination,formatTime} from '../admin/AdminUI';
import {display} from './definitions';
import {useUnsavedChanges} from './ScreenForm';
import PaymentReceiptPreview from './PaymentReceiptPreview';
import {paymentApi,paymentKinds,paymentReviewPayload,paymentToday,submissionStatuses,validatePaymentReview} from './studentPayments';
import './student-payments.css';

function ReviewDialog({id,onLogout,onClose,onReviewed}){
 const [data,setData]=useState(null),[loading,setLoading]=useState(true),[loadError,setLoadError]=useState(''),[revision,setRevision]=useState(0);
 const [decision,setDecision]=useState('confirm'),[values,setValues]=useState({bankReference:'',statementAmount:'',statementMemo:'',statementDate:'',identityVerified:false,amountVerified:false,referenceVerified:false,changeNote:'',reviewNote:''});
 const [busy,setBusy]=useState(false),[error,setError]=useState(''),[errors,setErrors]=useState({}),[stale,setStale]=useState(false),[copyNotice,setCopyNotice]=useState('');
 const mounted=useRef(false),pending=useRef(false),request=useRef(null),dirty=useUnsavedChanges();
 useEffect(()=>{mounted.current=true;return()=>{mounted.current=false;request.current?.abort();};},[]);
 useEffect(()=>{
  const controller=new AbortController();setLoading(true);setLoadError('');setError('');setStale(false);setErrors({});
  api(paymentApi+'/review/'+encodeURIComponent(id),{signal:controller.signal}).then(result=>{if(!controller.signal.aborted)setData(result);}).catch(cause=>{if(!controller.signal.aborted){if(cause.status===401)onLogout?.();else setLoadError(cause.message);}}).finally(()=>{if(!controller.signal.aborted)setLoading(false);});
  return()=>controller.abort();
 },[id,revision]);
 function close(){if(busy)return;if(dirty.current&&!window.confirm('Bạn có thay đổi chưa lưu. Đóng phần đối chiếu bill?'))return;dirty.current=false;onClose();}
 function change(key,value){dirty.current=true;setValues(current=>({...current,[key]:value}));setErrors(current=>({...current,[key]:undefined}));}
 function reload(){if(dirty.current&&!window.confirm('Tải lại sẽ bỏ các thông tin đối chiếu chưa lưu. Tiếp tục?'))return;dirty.current=false;setValues({bankReference:'',statementAmount:'',statementMemo:'',statementDate:'',identityVerified:false,amountVerified:false,referenceVerified:false,changeNote:'',reviewNote:''});setRevision(value=>value+1);}
 async function copyMemo(){try{await navigator.clipboard.writeText(data.expectedTransferMemo||data.submission.transferMemo);setCopyNotice('Đã sao chép nội dung bắt buộc.');}catch{setCopyNotice('Bạn có thể chọn và sao chép nội dung trong ô.');}}
 async function submit(event){
  event.preventDefault();if(pending.current||stale||data?.submission?.status!=='PENDING')return;
  let invalid={};if(decision==='confirm')invalid=validatePaymentReview(values,data.submission,data.expectedTransferMemo||data.submission.transferMemo);
  else if(!values.reviewNote.trim()||values.reviewNote.trim().length>1000)invalid.reviewNote='Nhập nội dung sai lệch cần làm rõ, tối đa 1.000 ký tự.';
  setErrors(invalid);setError('');if(Object.keys(invalid).length){setError('Kiểm tra các thông tin đối chiếu trước khi lưu.');return;}
  pending.current=true;setBusy(true);const controller=new AbortController();request.current=controller;
  try{
   const payload=decision==='confirm'?paymentReviewPayload(values,data.submission.version):{version:data.submission.version,reviewNote:values.reviewNote.trim()};
   const result=await api(paymentApi+'/review/'+encodeURIComponent(id)+'/'+decision,{method:'POST',body:JSON.stringify(payload),signal:controller.signal});
   if(mounted.current&&!controller.signal.aborted){dirty.current=false;onReviewed(result);}
  }catch(cause){if(mounted.current&&!controller.signal.aborted){if(cause.status===401)onLogout?.();else{setError(cause.message);setErrors(cause.fields||{});setStale(cause.status===409||cause.status===404);}}}
  finally{pending.current=false;if(mounted.current&&!controller.signal.aborted)setBusy(false);}
 }
 const submission=data?.submission,contract=data?.contract,bank=data?.bank||{},pendingReview=submission?.status==='PENDING';
 const fieldError=key=>errors[key]&&<small className="field-error">{errors[key]}</small>;
 return <AdminDialog title="Đối chiếu bill chuyển khoản" onClose={close} busy={busy}><div className="admin-dialog-body student-payment-review-body">
  {loading?<AdminLoading/>:loadError?<AdminLoading error={loadError} onRetry={()=>setRevision(value=>value+1)}/>:submission?<>
   <div className="student-payment-review-summary"><h3>{contract?.code||submission.contractCode} · {contract?.studentName||submission.studentName}</h3><p>Mã học viên: {contract?.studentCode||submission.studentCode} · {contract?.courseName}</p><span className={'admin-badge'+(submission.status==='CONFIRMED'?'':' is-warning')}>{submissionStatuses[submission.status]||submission.status}</span></div>
   <div className="student-payment-review-grid"><PaymentReceiptPreview fileId={submission.receiptFileId} filename={submission.filename||submission.receiptFilename} onLogout={onLogout}/><section className="student-payment-review-info"><h3>Thông tin cần đối chiếu</h3><dl className="screen-facts">
    <div><dt>Khoản thu học viên khai báo</dt><dd>{paymentKinds[submission.kind]}</dd></div><div><dt>Số tiền trên bill</dt><dd>{display(submission.amount,'amount')}</dd></div>
    {submission.kind==='COMBINED'&&<><div><dt>Phân bổ học phí</dt><dd>{display(submission.tuitionAmount,'amount')}</dd></div><div><dt>Phân bổ tiền cọc</dt><dd>{display(submission.depositAmount,'amount')}</dd></div></>}
    <div><dt>Người chuyển học viên khai báo</dt><dd>{submission.payerName}</dd></div><div><dt>Ngày chuyển học viên khai báo</dt><dd>{display(submission.paidOn,'paidOn')}</dd></div>
    <div><dt>Mã giao dịch học viên khai báo</dt><dd>{submission.declaredBankReference||'Chưa cung cấp'}</dd></div><div><dt>Ngày gửi bill</dt><dd>{formatTime(submission.createdAt)}</dd></div>
    <div><dt>Ngân hàng nhận</dt><dd>{bank.bankName}</dd></div><div><dt>Tài khoản nhận</dt><dd>{bank.accountNumber}<br/>{bank.accountHolder}</dd></div>
   </dl>{submission.note&&<p className="payment-review-note"><strong>Ghi chú của học viên:</strong> {submission.note}</p>}
    <label className="admin-field">Nội dung chuyển khoản bắt buộc<div className="payment-copy-field"><input readOnly value={data.expectedTransferMemo||submission.transferMemo||''} onFocus={event=>event.target.select()} aria-label="Nội dung chuyển khoản cần khớp với sao kê"/><button className="admin-secondary" type="button" onClick={copyMemo} aria-label="Sao chép nội dung chuyển khoản"><Copy size={15}/></button></div></label>{copyNotice&&<p className="payment-copy-notice" role="status">{copyNotice}</p>}
    <p className="student-payment-waiting">Bill là thông tin học viên gửi. Chỉ xác nhận sau khi tìm thấy khoản tiền thực nhận và so khớp với sao kê ngân hàng.</p>
   </section></div>
   {!pendingReview?<><p className="admin-message">Bill này đã được xử lý. {submission.reviewNote||''}</p>{submission.transactionId&&<p className="admin-muted">Khoản tiền đã được gắn với giao dịch được kế toán xác nhận.</p>}</>:<form className="student-payment-review-form" onSubmit={submit} aria-busy={busy}><AdminMessage error={error}/><fieldset disabled={busy||stale}>
    <label className="admin-field">Kết quả đối chiếu<select value={decision} onChange={event=>{dirty.current=true;setDecision(event.target.value);setErrors({});setError('');}}><option value="confirm">Đã khớp sao kê — xác nhận nhận tiền</option><option value="mismatch">Có sai lệch — yêu cầu học viên làm rõ</option></select></label>
    {decision==='confirm'?<><div className="admin-field-grid">
     <label className="admin-field">Mã giao dịch trên sao kê *<input value={values.bankReference} onChange={event=>change('bankReference',event.target.value)} required maxLength={120} autoComplete="off" aria-invalid={Boolean(errors.bankReference)}/>{fieldError('bankReference')}</label>
     <label className="admin-field">Số tiền thực nhận trên sao kê (đ) *<input type="number" inputMode="numeric" min="1" step="1" value={values.statementAmount} onChange={event=>change('statementAmount',event.target.value)} required aria-invalid={Boolean(errors.statementAmount)}/>{fieldError('statementAmount')}</label>
     <label className="admin-field">Ngày giao dịch trên sao kê *<input type="date" value={values.statementDate} onChange={event=>change('statementDate',event.target.value)} required max={paymentToday()} aria-invalid={Boolean(errors.statementDate)}/>{fieldError('statementDate')}</label>
     <label className="admin-field screen-field-wide">Nội dung chuyển khoản trên sao kê *<input value={values.statementMemo} onChange={event=>change('statementMemo',event.target.value)} required maxLength={120} autoComplete="off" aria-invalid={Boolean(errors.statementMemo)}/>{fieldError('statementMemo')}</label>
     <label className="admin-field screen-field-wide">Ý kiến xác nhận của kế toán *<textarea rows={2} required maxLength={1000} value={values.changeNote} onChange={event=>change('changeNote',event.target.value)} aria-invalid={Boolean(errors.changeNote)}/>{fieldError('changeNote')}</label>
    </div><div className="payment-review-checks">{[['identityVerified','Đã khớp học viên, hợp đồng và tài khoản nhận tiền'],['amountVerified','Đã khớp số tiền, khoản thu và nội dung chuyển khoản'],['referenceVerified','Đã kiểm tra mã giao dịch thực tế chưa được ghi nhận trước đây']].map(([key,label])=><label key={key}><input type="checkbox" checked={values[key]} onChange={event=>change(key,event.target.checked)} required/>{label}</label>)}</div>{fieldError('verified')}</>:<label className="admin-field">Sai lệch cần học viên làm rõ *<textarea rows={3} required value={values.reviewNote} onChange={event=>change('reviewNote',event.target.value)} maxLength={1000} aria-invalid={Boolean(errors.reviewNote)}/>{fieldError('reviewNote')}</label>}
   </fieldset><div className="admin-dialog-actions"><button type="button" className="admin-secondary" onClick={close} disabled={busy}>Đóng</button>{stale?<button type="button" onClick={reload}><RefreshCw size={14}/>Tải lại trạng thái</button>:<button type="submit" disabled={busy||(decision==='confirm'&&(!values.identityVerified||!values.amountVerified||!values.referenceVerified))}><Check size={15}/>{busy?'Đang lưu…':decision==='confirm'?'Xác nhận đã nhận tiền':'Ghi nhận sai lệch'}</button>}</div></form>}
  </>:null}
 </div></AdminDialog>;
}

export default function StudentPaymentReview({canReview,onLogout,onUpdated}){
 const [search,setSearch]=useState(''),[filter,setFilter]=useState({q:'',status:'PENDING',page:0}),[revision,setRevision]=useState(0);
 const [data,setData]=useState(null),[loading,setLoading]=useState(true),[error,setError]=useState(''),[notice,setNotice]=useState(''),[selected,setSelected]=useState(null);
 useEffect(()=>{const timer=setTimeout(()=>setFilter(current=>current.q===search.trim()?current:{...current,q:search.trim(),page:0}),300);return()=>clearTimeout(timer);},[search]);
 useEffect(()=>{const controller=new AbortController();setLoading(true);setError('');api(paymentApi+'/review?'+new URLSearchParams(filter),{signal:controller.signal}).then(result=>{if(!controller.signal.aborted)setData(result);}).catch(cause=>{if(!controller.signal.aborted){if(cause.status===401)onLogout?.();else setError(cause.message);}}).finally(()=>{if(!controller.signal.aborted)setLoading(false);});return()=>controller.abort();},[filter,revision]);
 function reviewed(result){const message=result.notice||'Đã lưu kết quả đối chiếu bill.';setSelected(null);setNotice(message);setRevision(value=>value+1);onUpdated?.(message);}
 return <section className="admin-card student-payment-review-queue" aria-label="Bill học viên gửi kế toán"><header className="student-payment-heading"><div><h2>Bill học viên gửi</h2><p>Đối chiếu với sao kê trước khi ghi nhận tiền; bill đang chờ chưa được tính vào học phí đã nộp.</p></div><button type="button" className="admin-secondary" onClick={()=>setRevision(value=>value+1)} disabled={loading}><RefreshCw size={15}/>Tải lại bill</button></header><AdminMessage notice={notice}/>
  <div className="screen-toolbar"><label className="screen-search"><Search size={16}/><input type="search" maxLength={200} value={search} onChange={event=>setSearch(event.target.value)} placeholder="Tìm học viên hoặc hợp đồng…" aria-label="Tìm bill học viên"/></label><select value={filter.status} onChange={event=>setFilter(current=>({...current,status:event.target.value,page:0}))} aria-label="Trạng thái đối chiếu bill">{['PENDING','MISMATCH','CONFIRMED'].map(status=><option key={status} value={status}>{submissionStatuses[status]}</option>)}</select></div>
  {loading?<AdminLoading/>:error?<AdminLoading error={error} onRetry={()=>setRevision(value=>value+1)}/>:<><div className="admin-table-scroll"><table className="admin-table"><thead><tr><th>Học viên / hợp đồng</th><th>Khoản thu</th><th>Số tiền khai báo</th><th>Ngày gửi</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>{(data?.items||[]).map(row=><tr key={row.id}><td><strong>{row.studentName}</strong><small>{row.studentCode} · {row.contractCode}</small></td><td>{paymentKinds[row.kind]}</td><td>{display(row.amount,'amount')}</td><td>{formatTime(row.createdAt)}</td><td><span className={'admin-badge'+(row.status==='CONFIRMED'?'':' is-warning')}>{submissionStatuses[row.status]||row.status}</span></td><td>{canReview?<button type="button" className="admin-text-button" onClick={()=>setSelected(row.id)}>{row.status==='PENDING'?'Đối chiếu bill':'Xem kết quả'}</button>:<span className="admin-muted">Chỉ xem danh sách</span>}</td></tr>)}</tbody></table>{!data?.items?.length&&<p className="admin-empty">Không có bill phù hợp với bộ lọc.</p>}</div><AdminPagination page={data?.page??filter.page} total={data?.total||0} totalPages={data?.totalPages||0} size={data?.size||25} onPage={page=>setFilter(current=>({...current,page}))}/></>}
  {selected&&<ReviewDialog id={selected} onLogout={onLogout} onClose={()=>setSelected(null)} onReviewed={reviewed}/>}
 </section>;
}
