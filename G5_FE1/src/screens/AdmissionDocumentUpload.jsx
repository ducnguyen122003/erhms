import {useEffect,useRef,useState} from 'react';
import {Check,CircleCheck,FileText,Upload,X} from 'lucide-react';
import {api} from '../api';
import {AdminDialog,AdminMessage} from '../admin/AdminUI';
import {useUnsavedChanges} from './ScreenForm';
import {ProfileDocumentStatus} from './ProfileDocuments';
import {uploadDocumentTypes,documentGroups,emptyDocumentUploads,uploadedFileCount,validateDocumentUploads,pdfSelectionError} from './admissionDocuments';
import './admission-documents.css';

export default function AdmissionDocumentUpload({record,busy,error,errors={},onSave,onLogout,initialDocuments=[],editing=false,onCancel}){
 const [uploads,setUploads]=useState(()=>emptyDocumentUploads(initialDocuments)),[activeCode,setActiveCode]=useState(null),[note,setNote]=useState(''),[uploading,setUploading]=useState(false),[submitting,setSubmitting]=useState(false),[progress,setProgress]=useState(''),[uploadError,setUploadError]=useState(''),[validation,setValidation]=useState({});
 const dirty=useUnsavedChanges(),pending=useRef(false),mounted=useRef(false),request=useRef(null);
 const locked=busy||uploading||submitting,groups=documentGroups(uploads),items=uploadDocumentTypes(uploads),activeItem=items.find(item=>item.code===activeCode),activeFiles=uploads[activeCode]?.files||[];
 useEffect(()=>{mounted.current=true;return()=>{mounted.current=false;request.current?.abort();};},[]);
 function openItem(code){
  if(locked||pending.current)return;
  setValidation({});setUploadError('');setActiveCode(code);
 }
 function itemIssue(code){const index=groups.findIndex(group=>group.type===code);return index<0?null:errors[`documents.${index}.files`]||errors[`documents.${index}.type`];}
 function remove(code,id){dirty.current=true;setValidation({});setUploads(current=>({...current,[code]:{files:current[code].files.filter(file=>file.id!==id)}}));}
 async function upload(event,code){
  const input=event.currentTarget,files=[...input.files||[]];
  if(!files.length||pending.current||locked)return;
  const invalid=pdfSelectionError(files,uploadedFileCount(uploads));
  if(invalid){setUploadError(invalid);input.value='';return;}
  pending.current=true;setUploading(true);setUploadError('');setValidation({});
  const controller=new AbortController();request.current=controller;
  try{
   for(let index=0;index<files.length;index++){
    const file=files[index];setProgress(`Đang tải ${index+1}/${files.length}: ${file.name}`);
    const body=new FormData();body.append('file',file);body.append('screen','AdmissionConsultation');
    const saved=await api('/api/workspace/files',{method:'POST',body,signal:controller.signal});
    if(!mounted.current||controller.signal.aborted)return;
    dirty.current=true;setUploads(current=>({...current,[code]:{files:[...current[code].files,saved]}}));
   }
  }catch(cause){if(mounted.current&&!controller.signal.aborted){if(cause.status===401)onLogout?.();else setUploadError(cause.message);}}
  finally{pending.current=false;input.value='';if(mounted.current){setUploading(false);setProgress('');}}
 }
 async function submit(event){
  event.preventDefault();if(locked||pending.current)return;
  const invalid=validateDocumentUploads(uploads,{allowEmpty:editing});setValidation(invalid);setUploadError('');if(Object.keys(invalid).length)return;
  pending.current=true;setSubmitting(true);
  try{await onSave({version:record.version,documents:documentGroups(uploads),note},()=>{dirty.current=false;});}
  finally{pending.current=false;if(mounted.current)setSubmitting(false);}
 }
 return <>
  <section className="admission-document-upload" aria-labelledby="admission-document-heading">
   <h3 id="admission-document-heading">{editing?'Bổ sung / chỉnh sửa hồ sơ':'Bản scan hồ sơ giấy'}</h3>
   <p className="admin-muted">Bấm Tải PDF ở từng mục để tải một hoặc nhiều tệp. Mục đã tải thành công sẽ hiện màu xanh. Mỗi tệp tối đa 2 MB, toàn bộ hồ sơ tối đa 50 tệp.</p>
   <form onSubmit={submit} aria-busy={locked}>
    <AdminMessage error={error}/><AdminMessage error={validation.documents||errors.documents}/>
    <ProfileDocumentStatus documents={groups.map(group=>({documentType:group.type}))}/>
    <fieldset disabled={locked} className="admission-document-picker"><legend className="admin-sr-only">Danh sách giấy tờ hồ sơ</legend>{items.map(item=>{
     const row=uploads[item.code],imported=row.files.length>0,issue=itemIssue(item.code);
     return <div key={item.code} className={'admission-document-item'+(imported?' is-uploaded':'')+(issue?' is-invalid':'')}>
      <div className="admission-document-row">
       <div className="admission-document-status"><span className="admission-document-status-icon" aria-hidden="true">{imported?<CircleCheck size={20}/>:<FileText size={20}/>}</span><span><strong>{item.label}</strong><small role="status">{imported?`Đã tải ${row.files.length} tệp PDF`:'Chưa tải PDF'}</small></span></div>
       <button type="button" className="admin-secondary" onClick={()=>openItem(item.code)} aria-haspopup="dialog" aria-describedby={issue?'document-error-'+item.code:undefined} aria-label={(imported?'Quản lý PDF cho ':'Tải PDF cho ')+item.label}><Upload size={14}/>{imported?'Quản lý PDF':'Tải PDF'}</button>
      </div>
      {issue&&<p className="field-error admission-document-item-error" id={'document-error-'+item.code} role="alert">{issue}</p>}
     </div>;
    })}</fieldset>
    <fieldset disabled={locked} className="admission-document-note"><label className="admin-field">Ghi chú gửi quản lý<textarea rows={3} maxLength={1800} value={note} onChange={event=>{dirty.current=true;setNote(event.target.value);}}/></label></fieldset>
    <div className="admission-document-submit"><p className="admission-document-progress" role="status">Đã tải {groups.length} loại giấy tờ · {uploadedFileCount(uploads)} tệp PDF</p><div>{editing&&<button type="button" className="admin-secondary" disabled={locked} onClick={()=>{if(!dirty.current||window.confirm('Bỏ các thay đổi hồ sơ chưa lưu?')){dirty.current=false;onCancel?.();}}}>Hủy chỉnh sửa</button>}<button type="submit" disabled={locked}><Check size={15}/>{uploading?'Đang tải tệp…':busy||submitting?'Đang lưu…':editing?'Lưu thay đổi hồ sơ':'Gửi hồ sơ'}</button></div></div>
   </form>
  </section>
  {activeItem&&<AdminDialog title={'Tải PDF · '+activeItem.label} busy={locked} onClose={()=>setActiveCode(null)}><div className="admin-dialog-body admission-document-item-dialog" aria-busy={locked}>
   <p className="admin-muted">{activeCode==='LEGACY_SCAN'?'Bản scan cũ được giữ nguyên. Tải giấy tờ mới vào các mục tương ứng trong danh sách hồ sơ.':`Tải một hoặc nhiều file PDF cho ${activeItem.label}. Mỗi tệp tối đa 2 MB.`}</p>
   <AdminMessage error={uploadError||itemIssue(activeCode)}/>
   <fieldset disabled={locked} className="admission-document-files">
    {activeCode!=='LEGACY_SCAN'&&<label className="admission-document-upload-button"><Upload size={15}/>Chọn file PDF<input type="file" multiple accept=".pdf,application/pdf" aria-label={'Tải file PDF cho '+activeItem.label} onChange={event=>upload(event,activeCode)} data-initial-focus/></label>}
    {!activeFiles.length&&<p className="admin-muted">Chưa có tệp. Chọn ít nhất một PDF cho mục này.</p>}
    <ul>{activeFiles.map(file=><li key={file.id}><a href={'/api/workspace/files/'+file.id} download><FileText size={14}/>{file.filename}</a><button type="button" className="icon-btn" onClick={()=>remove(activeCode,file.id)} aria-label={'Bỏ tệp '+file.filename}><X size={15}/></button></li>)}</ul>
   </fieldset>
   <p className="admission-document-progress" role="status">{progress||`${activeFiles.length} tệp PDF cho ${activeItem.label}`}</p>
   <div className="admin-dialog-actions"><button type="button" disabled={locked} onClick={()=>setActiveCode(null)}><Check size={15}/>Xong</button></div>
  </div></AdminDialog>}
 </>;
}
