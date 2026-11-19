import {useEffect,useState} from 'react';
import {Download,RefreshCw} from 'lucide-react';
import {api} from '../api';
import {AdminLoading,AdminMessage} from '../admin/AdminUI';

export default function PaymentReceiptPreview({fileId,filename,onLogout}){
 const [file,setFile]=useState(null),[error,setError]=useState(''),[loading,setLoading]=useState(true),[revision,setRevision]=useState(0);
 useEffect(()=>{
  const controller=new AbortController();let url;setLoading(true);setError('');setFile(null);
  if(!fileId){setLoading(false);return()=>controller.abort();}
  api('/api/workspace/files/'+encodeURIComponent(fileId),{responseType:'blob',cache:'no-store',signal:controller.signal}).then(blob=>{
   if(controller.signal.aborted)return;url=URL.createObjectURL(blob);setFile({url,image:blob.type.startsWith('image/')});
  }).catch(cause=>{if(!controller.signal.aborted){if(cause.status===401)onLogout?.();else setError(cause.message);}}).finally(()=>{if(!controller.signal.aborted)setLoading(false);});
  return()=>{controller.abort();if(url)URL.revokeObjectURL(url);};
 },[fileId,revision]);
 return <section className="payment-receipt-preview" aria-label="Bill học viên đã gửi"><header><h3>Bill chuyển khoản của học viên</h3>{fileId&&<a className="screen-file-link" href={'/api/workspace/files/'+encodeURIComponent(fileId)} download><Download size={14}/>Tải bill</a>}</header>
  {loading?<AdminLoading/>:error?<><AdminMessage error={error}/><button className="admin-secondary" type="button" onClick={()=>setRevision(value=>value+1)}><RefreshCw size={14}/>Tải lại bill</button></>:file?file.image?<img src={file.url} alt={'Bill chuyển khoản '+(filename||'của học viên')}/>:<iframe src={file.url} title={'Bill chuyển khoản '+(filename||'của học viên')}/>:<p className="admin-muted">Chưa có tệp bill để xem.</p>}
 </section>;
}
