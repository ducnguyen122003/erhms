import {useEffect,useId,useRef,useState} from 'react';
import {Download,FileSpreadsheet,Upload} from 'lucide-react';
import {api} from '../api';
import {AdminDialog,AdminMessage} from '../admin/AdminUI';
import './admission-import.css';

const maxFileSize=2*1024*1024;
const modes={
 admissions:{
  endpoint:'/api/workspace/admissions',filename:'mau-nhap-hoc-sinh.xlsx',title:'Nhập học sinh từ Excel',sheet:'Hoc sinh',people:'học sinh',
  description:<>Thêm danh sách học sinh vào hồ sơ đăng ký tư vấn & tuyển sinh với trạng thái <strong>Mới tiếp nhận</strong>.</>,
  fields:'Các cột có (*) là bắt buộc.'
 },
 studentAccounts:{
  endpoint:'/api/workspace/student-accounts',filename:'mau-nhap-hoc-vien.xlsx',title:'Nhập học viên từ Excel',sheet:'Hoc vien',people:'học viên',
  description:<>Thêm học viên trực tiếp vào danh sách cấp tài khoản, không cần chuyển hồ sơ từ tuyển sinh. Sau khi nhập, bấm <strong>Tạo tài khoản</strong> ở từng học viên để gửi thông tin đăng nhập qua email.</>,
  fields:'Bắt buộc điền họ tên, số điện thoại, email và khóa quan tâm.'
 }
};

export default function StudentExcelImport({mode='admissions',onImported,onError,onLogout,disabled=false}) {
 const config=modes[mode],inputId=useId(),limitsId=useId();
 const [open,setOpen]=useState(false),[file,setFile]=useState(null),[busy,setBusy]=useState(false),[downloading,setDownloading]=useState(false),[error,setError]=useState(''),[errors,setErrors]=useState({});
 const pending=useRef(false),mounted=useRef(false);
 useEffect(()=>{mounted.current=true;return()=>{mounted.current=false;};},[]);

 async function downloadTemplate(){
  if(pending.current)return;
  pending.current=true;setDownloading(true);onError('');
  try {
   const blob=await api(config.endpoint+'/import-template',{responseType:'blob'});
   if(!mounted.current)return;
   const url=URL.createObjectURL(blob),link=document.createElement('a');
   link.href=url;link.download=config.filename;document.body.appendChild(link);link.click();link.remove();
   setTimeout(()=>URL.revokeObjectURL(url),1000);
  } catch(cause) {
   if(!mounted.current)return;
   if(cause.status===401)onLogout();else if(open)setError(cause.message);else onError(cause.message);
  } finally {pending.current=false;if(mounted.current)setDownloading(false);}
 }

 function selectFile(event){
  const selected=event.target.files?.[0];setFile(null);setError('');setErrors({});
  if(!selected)return;
  if(!/\.xlsx$/i.test(selected.name)){setError('Vui lòng chọn file Excel có đuôi .xlsx.');return;}
  if(!selected.size||selected.size>maxFileSize){setError('File cần có dữ liệu và dung lượng không quá 2 MB.');return;}
  setFile(selected);
 }

 async function submit(event){
  event.preventDefault();if(!file||pending.current)return;
  pending.current=true;setBusy(true);setError('');setErrors({});
  const body=new FormData();body.append('file',file);
  try {
   const result=await api(config.endpoint+'/import',{method:'POST',body});
   if(!mounted.current)return;
   setOpen(false);setFile(null);onImported(result);
  } catch(cause) {
   if(!mounted.current)return;
   if(cause.status===401)onLogout();else{setError(cause.message);setErrors(cause.fields||{});}
  } finally {pending.current=false;if(mounted.current)setBusy(false);}
 }

 const waiting=busy||downloading;
 return <>
  <button type="button" className="admin-secondary" onClick={downloadTemplate} disabled={waiting||disabled}><Download size={15}/>{downloading?'Đang tải mẫu…':'Tải mẫu Excel'}</button>
  <button type="button" onClick={()=>{setFile(null);setError('');setErrors({});setOpen(true);}} disabled={waiting||disabled}><Upload size={15}/>Nhập từ Excel</button>
  {open&&<AdminDialog title={config.title} onClose={()=>setOpen(false)} busy={waiting}>
   <form className="admin-dialog-body admission-import" onSubmit={submit} aria-busy={waiting}>
    <p className="admin-muted">{config.description}</p>
    <div className="admission-import-template">
     <FileSpreadsheet size={27} aria-hidden="true"/>
     <div><strong>1. Tải và điền file mẫu</strong><p>Điền sheet “{config.sheet}”, giữ nguyên hàng tiêu đề. {config.fields} Xem ví dụ trong sheet “Huong dan”.</p></div>
     <button type="button" className="admin-secondary" onClick={downloadTemplate} disabled={waiting}><Download size={14}/>{downloading?'Đang tải…':'Tải mẫu'}</button>
    </div>
    <label className="admin-field admission-import-file" htmlFor={inputId}>2. Chọn file đã điền
     <input id={inputId} type="file" accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" onChange={selectFile} disabled={waiting} aria-describedby={limitsId} data-initial-focus/>
    </label>
    <p id={limitsId} className="admin-muted">Định dạng .xlsx · Tối đa 2 MB và 500 {config.people} mỗi lần nhập. Giữ số 0 đầu số điện thoại bằng định dạng Text trong file mẫu.</p>
    {file&&<p className="admission-import-selected"><FileSpreadsheet size={16} aria-hidden="true"/><span>{file.name} <small>({Math.ceil(file.size/1024)} KB)</small></span></p>}
    <AdminMessage error={error}/>
    {Object.keys(errors).length>0&&<div className="admission-import-errors" role="region" aria-label="Chi tiết lỗi trong file" tabIndex={0}><ul>{Object.entries(errors).map(([key,message])=><li key={key}>{message}</li>)}</ul></div>}
    <p className="admin-muted">Hệ thống kiểm tra toàn bộ file trước khi lưu. Nếu có lỗi hoặc hồ sơ trùng, hãy sửa file rồi nhập lại.</p>
    <div className="admin-dialog-actions"><button type="button" className="admin-secondary" onClick={()=>setOpen(false)} disabled={waiting}>Hủy</button><button type="submit" disabled={!file||waiting}><Upload size={15}/>{busy?'Đang nhập dữ liệu…':'Nhập danh sách'}</button></div>
   </form>
  </AdminDialog>}
 </>;
}
