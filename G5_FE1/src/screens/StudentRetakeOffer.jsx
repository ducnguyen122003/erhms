import {useState} from 'react';
import {ArrowLeft,ArrowRight} from 'lucide-react';
import {display} from './definitions';
import {contractPdfPath} from './contractPdf';

function classLabel(classroom){
 const name=[classroom.code,classroom.name].filter(Boolean).join(' · ');
 return [name,classroom.startsOn&&display(classroom.startsOn,'startsOn')].filter(Boolean).join(' — ');
}

export function StudentRetakeList({offers,onOpen}){
 return <section className="admin-card admin-list-card" aria-label="Lời mời học lại">
  {offers.length===0?<p className="admin-empty">Bạn chưa có lời mời học lại.</p>
   :<div className="admin-table-scroll"><table className="admin-table">
    <thead><tr><th>Khóa học</th><th>Hợp đồng trước</th><th>Học phí học lại</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
    <tbody>{offers.map(offer=><tr key={offer.id}>
     <td>{offer.courseName}</td><td>{offer.contractCode}</td><td>{display(offer.retakeTuition,'tuitionDue')}</td>
     <td>{offer.retakeContractId?'Đã đăng ký':'Chờ đăng ký'}</td>
     <td><button type="button" className="admin-text-button" onClick={()=>onOpen(offer.id)}>Mở lời mời<ArrowRight size={14} aria-hidden="true"/></button></td>
    </tr>)}</tbody>
   </table></div>}
 </section>;
}

export default function StudentRetakeOffer({record,classes=[],canRegister,canPay,busy,errors={},onRegister,onPay,onBack}){
 const [classId,setClassId]=useState(''),[accepted,setAccepted]=useState(false);
 const selectedClass=classes.find(classroom=>classroom.id===classId)||(classes.length===1?classes[0]:null);
 const alreadyRegistered=Boolean(record.retakeContractId);
 function submit(event){
  event.preventDefault();
  if(!busy&&canRegister&&selectedClass&&accepted)onRegister({classId:selectedClass.id,accepted});
 }
 return <section className="admin-card screen-settlement" aria-label="Đăng ký học lại">
  <h2>{record.courseName}</h2>
  <dl className="screen-facts">
   <div><dt>Hợp đồng trước</dt><dd>{record.contractCode}</dd></div>
   <div><dt>Học phí gốc</dt><dd>{display(record.originalTuition,'tuitionDue')}</dd></div>
   <div><dt>Học phí học lại ({record.retakePercent||70}%)</dt><dd><b>{display(record.retakeTuition,'tuitionDue')}</b></dd></div>
   {record.depositDue!=null&&<div><dt>Tiền cọc thiết bị theo hợp đồng</dt><dd>{display(record.depositDue,'depositDue')}</dd></div>}
  </dl>
  {alreadyRegistered?<p className="admin-message" role="status">Bạn đã đăng ký học lại. Mở hợp đồng mới để xem thông tin và thanh toán.</p>
   :!classes.length?<p className="admin-message" role="status">Chưa có lớp học lại còn chỗ. Bạn có thể mở lại thông báo này khi có lịch mới.</p>
   :canRegister&&<form onSubmit={submit} aria-busy={busy}>
    <fieldset disabled={busy}>
     <label className="admin-field">Lớp học lại *
      <select value={selectedClass?.id||''} required aria-invalid={Boolean(errors.classId)} onChange={event=>{setClassId(event.target.value);setAccepted(false);}}>
       <option value="" disabled>Chọn lớp học lại</option>
       {classes.map(classroom=><option key={classroom.id} value={classroom.id}>{classLabel(classroom)}</option>)}
      </select>
      {errors.classId&&<small className="field-error" role="alert">{errors.classId}</small>}
     </label>
     {selectedClass&&<dl className="screen-facts" aria-live="polite">
      <div><dt>Ngày bắt đầu</dt><dd>{display(selectedClass.startsOn,'startsOn')}</dd></div>
      <div><dt>Ngày kết thúc</dt><dd>{display(selectedClass.endsOn,'endsOn')}</dd></div>
      {selectedClass.weekdays?.length>0&&<div><dt>Ngày học trong tuần</dt><dd>{display(selectedClass.weekdays,'weekdays')}</dd></div>}
      {selectedClass.startTime&&selectedClass.endTime&&<div><dt>Giờ học</dt><dd>{String(selectedClass.startTime).slice(0,5)} – {String(selectedClass.endTime).slice(0,5)}</dd></div>}
      {selectedClass.lecturerName&&<div><dt>Giảng viên</dt><dd>{selectedClass.lecturerName}</dd></div>}
      {selectedClass.remainingSlots!=null&&<div><dt>Số chỗ còn lại</dt><dd>{selectedClass.remainingSlots}</dd></div>}
     </dl>}
     <h3>Điều khoản học lại</h3>
     <p className="admin-muted">Hợp đồng học lại áp dụng điều khoản của hợp đồng trước với lớp, thời gian học và học phí hiển thị ở trên. Sau khi đăng ký, bạn sẽ được chuyển đến phần nộp tiền theo hợp đồng mới.</p>
     {canPay&&record.originalContractId&&<a className="screen-file-link" href={contractPdfPath(record.originalContractId)} target="_blank" rel="noreferrer">Xem PDF hợp đồng trước</a>}
     {record.terms&&<p className="screen-body">{record.terms}</p>}
     <label className="admin-field"><span className="screen-checkbox"><input type="checkbox" checked={accepted} required aria-invalid={Boolean(errors.accepted)} onChange={event=>setAccepted(event.target.checked)}/>Tôi đồng ý với điều khoản và đăng ký lớp đã chọn.</span>
      {errors.accepted&&<small className="field-error" role="alert">{errors.accepted}</small>}
     </label>
    </fieldset>
    <button type="submit" disabled={busy||!selectedClass||!accepted}>{busy?'Đang đăng ký…':'Đăng ký và đến nộp tiền'}<ArrowRight size={15} aria-hidden="true"/></button>
   </form>}
  <footer className="screen-actions" style={{marginTop:18}}>
   <button type="button" className="admin-secondary" onClick={onBack} disabled={busy}><ArrowLeft size={14} aria-hidden="true"/>Quay lại</button>
   {alreadyRegistered&&canPay&&<button type="button" onClick={()=>onPay(record.retakeContractId)}>Mở hợp đồng mới và nộp tiền<ArrowRight size={15} aria-hidden="true"/></button>}
  </footer>
 </section>;
}
