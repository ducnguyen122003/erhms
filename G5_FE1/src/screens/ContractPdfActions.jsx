import {useEffect,useRef,useState} from 'react';
import {Download,Eye,FilePenLine} from 'lucide-react';
import {AdminDialog,AdminMessage} from '../admin/AdminUI';
import {contractPdfFilename,fetchContractPdf,savePdfBlob} from './contractPdf';
import {contractPdfFieldErrors} from './contractTemplate';
import './contract-template.css';

export default function ContractPdfActions({record,metadata,disabled=false,onLogout,onEdit}){
 const [busy,setBusy]=useState(''),[error,setError]=useState(''),[errors,setErrors]=useState({}),[preview,setPreview]=useState(null);
 const pending=useRef(false),mounted=useRef(false),request=useRef(null);
 useEffect(()=>{mounted.current=true;return()=>{mounted.current=false;request.current?.abort();};},[]);
 useEffect(()=>()=>{if(preview?.url)URL.revokeObjectURL(preview.url);},[preview?.url]);
 async function load(download){
  if(pending.current||disabled)return;
  pending.current=true;setBusy(download?'download':'preview');setError('');setErrors({});
  const controller=new AbortController();request.current=controller;
  try{
   const blob=await fetchContractPdf(record.id,download,controller.signal);
   if(!mounted.current||controller.signal.aborted)return;
   const filename=contractPdfFilename(record);
   if(download)savePdfBlob(blob,filename);else setPreview({blob,filename,url:URL.createObjectURL(blob)});
  }catch(cause){
   if(!mounted.current||controller.signal.aborted)return;
   if(cause.status===401)onLogout?.();else{setError(cause.message);setErrors(cause.fields||{});}
  }finally{pending.current=false;if(mounted.current&&!controller.signal.aborted)setBusy('');}
 }
 const invalid=contractPdfFieldErrors(errors,metadata);
 return <div className="contract-pdf-actions" aria-busy={Boolean(busy)}>
  <button type="button" className="admin-secondary" disabled={disabled||Boolean(busy)} onClick={()=>load(false)}><Eye size={15}/>{busy==='preview'?'Đang tạo bản xem…':'Xem PDF hợp đồng'}</button>
  <button type="button" className="admin-secondary" disabled={disabled||Boolean(busy)} onClick={()=>load(true)}><Download size={15}/>{busy==='download'?'Đang tạo PDF…':'Tải PDF hợp đồng'}</button>
  <AdminMessage error={error}/>
  {invalid.length>0&&<><ul className="contract-pdf-errors" aria-label="Thông tin cần bổ sung để xuất PDF">{invalid.map(item=><li key={item.key}><strong>{item.label}:</strong> {item.message}</li>)}</ul>{onEdit&&<button type="button" className="admin-text-button" disabled={Boolean(busy)} onClick={onEdit}><FilePenLine size={14}/>Bổ sung thông tin hợp đồng</button>}</>}
  {preview&&<AdminDialog title="Xem PDF hợp đồng" onClose={()=>setPreview(null)}><div className="contract-pdf-preview"><p>Bản xem trước dùng thông tin đã lưu trong hợp đồng. Tải PDF để in hoặc gửi cho học viên.</p><iframe src={preview.url} title={'Bản xem trước hợp đồng '+(record.code||record.id)}/><div className="admin-dialog-actions"><button type="button" className="admin-secondary" onClick={()=>setPreview(null)} data-initial-focus>Đóng</button><button type="button" onClick={()=>savePdfBlob(preview.blob,preview.filename)}><Download size={15}/>Tải PDF hợp đồng</button></div></div></AdminDialog>}
 </div>;
}
