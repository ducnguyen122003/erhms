import {useEffect,useRef,useState} from 'react';
import {Download,Eye} from 'lucide-react';
import {api} from '../api';
import {AdminDialog,AdminMessage} from '../admin/AdminUI';
import {display} from './definitions';
import {signingHelp} from './contractSigning';
import {savePdfBlob} from './contractPdf';
import ContractPdfActions from './ContractPdfActions';
import './contract-signing.css';

export function SignedContractScan({record,onLogout,disabled=false}){
 const [preview,setPreview]=useState(null),[busy,setBusy]=useState(false),[error,setError]=useState('');
 const request=useRef(null),pending=useRef(false),mounted=useRef(false);
 useEffect(()=>{mounted.current=true;return()=>{mounted.current=false;request.current?.abort();};},[]);
 useEffect(()=>()=>{if(preview?.url)URL.revokeObjectURL(preview.url);},[preview?.url]);
 async function view(){
  if(pending.current||disabled)return;
  pending.current=true;setBusy(true);setError('');const controller=new AbortController();request.current=controller;
  try{const blob=await api('/api/workspace/files/'+encodeURIComponent(record.signedScanFileId),{responseType:'blob',signal:controller.signal,cache:'no-store'});if(mounted.current&&!controller.signal.aborted)setPreview({blob,url:URL.createObjectURL(blob)});}
  catch(cause){if(mounted.current&&!controller.signal.aborted){if(cause.status===401&&onLogout)onLogout();else setError(cause.message);}}
  finally{pending.current=false;if(mounted.current&&!controller.signal.aborted)setBusy(false);}
 }
 if(!record.signedScanFileId)return null;
 return <div className="contract-scan-actions"><button type="button" className="admin-secondary" disabled={disabled||busy} onClick={view}><Eye size={15}/>{busy?'Đang tải bản ký…':'Xem bản scan đã ký'}</button><a className="screen-file-link" href={'/api/workspace/files/'+encodeURIComponent(record.signedScanFileId)} download><Download size={14}/>Tải bản scan</a><AdminMessage error={error}/>{preview&&<AdminDialog title="Bản scan hợp đồng đã ký" onClose={()=>setPreview(null)}><div className="contract-pdf-preview"><iframe src={preview.url} title="Bản scan hợp đồng đã ký"/><div className="admin-dialog-actions"><button type="button" className="admin-secondary" onClick={()=>setPreview(null)} data-initial-focus>Đóng</button><button type="button" onClick={()=>savePdfBlob(preview.blob,'hop-dong-da-ky-'+(record.code||record.id)+'.pdf')}><Download size={15}/>Tải bản scan</button></div></div></AdminDialog>}</div>;
}

export default function ContractSigningDetails({record,onLogout,disabled=false,showOriginal=false}){
 if(record.signatureMethod==='RETAKE_ACCEPTANCE')return <section className="contract-signing-summary" aria-label="Xác nhận hợp đồng học lại">
  <h3>Xác nhận hợp đồng học lại</h3>
  <p>Học viên đã đồng ý với điều khoản học lại trực tuyến.</p>
  {record.signatureApprovedAt&&<dl className="screen-facts"><div><dt>Thời điểm xác nhận</dt><dd>{display(record.signatureApprovedAt,'signatureApprovedAt')}</dd></div></dl>}
 </section>;
 const help=signingHelp(record);
 return <section className="contract-signing-summary" aria-label="Tiến trình ký hợp đồng"><h3>Ký hợp đồng và xác nhận</h3>{help&&<p>{help}</p>}
  {record.signatureReviewNote&&<p className={record.status==='SENT_FOR_SIGNATURE'?'contract-signing-returned':''}><strong>{record.status==='SENT_FOR_SIGNATURE'?'Yêu cầu bổ sung:':'Ý kiến QLHV:'}</strong> {record.signatureReviewNote}</p>}
  <dl className="screen-facts">{[['signatureSentAt','Ngày gửi tuyển sinh'],['signedOn','Ngày học viên ký'],['signatureSubmittedAt','Ngày gửi bản scan'],['signatureApprovedAt','Ngày QLHV xác nhận']].filter(([key])=>record[key]).map(([key,label])=><div key={key}><dt>{label}</dt><dd>{display(record[key],key)}</dd></div>)}</dl>
  {showOriginal&&<><p><strong>{record.code} · {record.studentName||record.applicantName}</strong><br/>{record.courseName||record.registeredCourseName}</p><ContractPdfActions record={record} onLogout={onLogout} disabled={disabled}/></>}
  <SignedContractScan record={record} onLogout={onLogout} disabled={disabled}/>
 </section>;
}
