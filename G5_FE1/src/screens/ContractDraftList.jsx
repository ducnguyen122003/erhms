import {ArrowUpRight,Send,Trash2} from 'lucide-react';
import {AdminDialog,AdminMessage} from '../admin/AdminUI';
import {columns,display,labels} from './definitions';
import {canSendContract,draftSendLimit} from './contractSigning';
import './contract-draft-list.css';

export function ContractDraftList({rows,canSend,canDelete,canOpen,selection,busy,renderStatus,onOpen,onSend,onDelete,onBatch,onSelect,onClear}){
 const eligible=canSend?rows.filter(canSendContract):[];
 const selectedOnPage=eligible.filter(row=>selection.has(row.id)).length;
 const allOnPage=eligible.length>0&&selectedOnPage===eligible.length;
 const fullSelection=selection.size>=draftSendLimit;

 return <>
  {canSend&&(eligible.length>0||selection.size>0)&&<div className="contract-draft-toolbar">
   <span role="status">Đã chọn <strong>{selection.size}</strong>/{draftSendLimit} bản nháp</span>
   {selection.size>0&&<button className="admin-text-button" type="button" disabled={busy} onClick={onClear}>Bỏ chọn tất cả</button>}
   <button type="button" disabled={busy||!selection.size} onClick={onBatch}><Send size={14}/>Chuyển các bản nháp đã chọn</button>
  </div>}
  <div className="admin-table-scroll"><table className="admin-table contract-draft-table"><thead><tr>
   {eligible.length>0&&<th className="contract-draft-select" scope="col"><input type="checkbox"
    aria-label="Chọn các bản nháp trên trang này"
    checked={allOnPage} disabled={busy||(!allOnPage&&fullSelection)}
    ref={input=>{if(input)input.indeterminate=selectedOnPage>0&&!allOnPage;}}
    onChange={event=>onSelect(eligible,event.target.checked)}/></th>}
   {columns.contracts.map(key=><th key={key} scope="col">{labels[key]}</th>)}<th scope="col">Thao tác</th>
  </tr></thead><tbody>{rows.map(row=>{
   const draft=canSendContract(row),selected=selection.has(row.id);
   return <tr key={row.id} className={selected?'contract-draft-selected':undefined}>
    {eligible.length>0&&<td className="contract-draft-select">{draft&&<input type="checkbox" aria-label={'Chọn bản nháp '+row.code}
     checked={selected} disabled={busy||(!selected&&fullSelection)} onChange={event=>onSelect([row],event.target.checked)}/>}</td>}
    {columns.contracts.map(key=><td key={key}>{key==='status'?renderStatus(row.status):display(row[key],key)}</td>)}
    <td><div className="contract-draft-actions">
     {canOpen&&<button className="admin-text-button" type="button" disabled={busy} onClick={()=>onOpen(row)}>Xem chi tiết<ArrowUpRight size={13}/></button>}
     {draft&&canSend&&<button className="admin-text-button" type="button" disabled={busy} onClick={()=>onSend(row)}><Send size={13}/>Chuyển tuyển sinh</button>}
     {draft&&canDelete&&<button className="admin-text-button admin-danger-link" type="button" disabled={busy} onClick={()=>onDelete(row)}><Trash2 size={13}/>Xóa bản nháp</button>}
     {!canOpen&&!(draft&&(canSend||canDelete))&&<span className="admin-muted">Chỉ xem</span>}
    </div></td>
   </tr>;
  })}</tbody></table></div>
 </>;
}

export function ContractDraftDialog({operation,busy,error,errors,onSave,onClose}){
 const {rows,kind}=operation,remove=kind==='delete',batch=kind==='batch';
 const title=remove?'Xóa bản nháp':batch?`Chuyển ${rows.length} bản nháp sang tuyển sinh`:'Chuyển hợp đồng sang tuyển sinh';
 const messages=[...new Set(Object.values(errors).filter(message=>message!==error))];

 function submit(event){
  event.preventDefault();
  if(busy)return;
  const {id,version}=rows[0];
  if(remove)onSave('delete-draft',id,{version});
  else if(batch)onSave('send-for-signature-batch','new',{contracts:rows.map(({id,version})=>({id,version}))});
  else onSave('send-for-signature',id,{version});
 }

 return <AdminDialog title={title} onClose={onClose} busy={busy}>
  <form className="admin-dialog-body" onSubmit={submit} aria-busy={busy}>
   <AdminMessage error={error}/>
   {messages.length>0&&<ul className="contract-draft-errors" aria-label="Thông tin cần kiểm tra">{messages.map(message=><li key={message}>{message}</li>)}</ul>}
   {remove?<p>Xóa bản nháp <strong>{rows[0].code}</strong> của học viên <strong>{rows[0].studentName}</strong>?</p>:<>
    <p>{batch?'Khi tất cả bản nháp đủ thông tin để xuất PDF, hệ thống gửi từng hợp đồng về tuyển sinh đã chuyển hồ sơ tương ứng.':'Hệ thống gửi bản PDF cho tuyển sinh đã chuyển hồ sơ để in và lấy chữ ký học viên.'}</p>
    <ul className="contract-draft-review" aria-label="Hợp đồng được chuyển">{rows.map(row=><li key={row.id}><strong>{row.code}</strong><span>{row.studentName}</span></li>)}</ul>
   </>}
   <div className="admin-dialog-actions">
    <button className="admin-secondary" type="button" data-initial-focus disabled={busy} onClick={onClose}>Hủy</button>
    <button className={remove?'admin-danger':undefined} type="submit" disabled={busy}>
     {remove?<Trash2 size={14}/>:<Send size={14}/>}{busy?'Đang xử lý…':remove?'Xóa bản nháp':'Chuyển tuyển sinh'}
    </button>
   </div>
  </form>
 </AdminDialog>;
}
