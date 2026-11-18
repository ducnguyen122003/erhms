import {CalendarDays,Plus} from 'lucide-react';
import {display} from './definitions';
import './class-management.css';

export default function ClassSchedule({record,sessions=[],canEdit,busy,onAdd,onEdit}){
 const open=['PLANNED','ACTIVE'].includes(record.status);
 return <section className="screen-related class-schedule" aria-labelledby="class-schedule-heading">
  <header className="class-schedule-heading"><h3 id="class-schedule-heading"><CalendarDays size={18}/>Lịch học của lớp</h3>{canEdit&&open&&<button type="button" className="admin-secondary" disabled={busy} onClick={onAdd}><Plus size={15}/>Thêm buổi học</button>}</header>
  <p className="admin-muted">Bắt đầu học: {display(record.startsOn,'startsOn')}{record.endsOn?' · Kết thúc dự kiến: '+display(record.endsOn,'endsOn'):''}</p>
  {sessions.length?<div className="admin-table-scroll"><table className="admin-table"><thead><tr><th>Nội dung buổi học</th><th>Bắt đầu</th><th>Kết thúc</th><th>Phòng học</th><th>Trạng thái</th>{canEdit&&<th>Thao tác</th>}</tr></thead><tbody>{sessions.map(session=><tr key={session.id}><td>{session.title}</td><td>{display(session.startsAt,'startsAt')}</td><td>{display(session.endsAt,'endsAt')}</td><td>{session.room||'—'}</td><td>{session.attendanceFinalized?'Đã chốt điểm danh':'Đã xếp lịch'}</td>{canEdit&&<td>{open&&!session.attendanceFinalized?<button type="button" className="admin-text-button" disabled={busy} onClick={()=>onEdit(session)}>Sửa lịch</button>:<span className="admin-muted">Chỉ xem</span>}</td>}</tr>)}</tbody></table></div>:<p className="admin-muted">Lớp chưa có lịch học. Thêm buổi học để chọn ngày, giờ và phòng học.</p>}
 </section>;
}
