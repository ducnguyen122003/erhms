import {CalendarDays,Plus} from 'lucide-react';
import {display} from './definitions';
import {classPlanYear,weeklyPlanLabel} from './classPlanning';
import './class-management.css';

export default function ClassSchedule({record,sessions=[],canEdit,busy,onAdd,onEdit}){
 const open=['PLANNED','ACTIVE'].includes(record.status);
 const editable=canEdit&&!record.planFixed;
 return <section className="screen-related class-schedule" aria-labelledby="class-schedule-heading">
  <header className="class-schedule-heading"><h3 id="class-schedule-heading"><CalendarDays size={18}/>Lịch học của lớp</h3>{editable&&open&&<button type="button" className="admin-secondary" disabled={busy} onClick={onAdd}><Plus size={15}/>Thêm buổi học</button>}</header>
  <p className="admin-muted">Bắt đầu học: {display(record.startsOn,'startsOn')}{record.endsOn?' · Kết thúc dự kiến: '+display(record.endsOn,'endsOn'):''}</p>
  {record.planFixed&&<div className="class-form-hint"><p><strong>Kế hoạch năm {classPlanYear(record)} đã chốt</strong></p><p>{weeklyPlanLabel(record)}{record.classroomName?' · '+record.classroomName:''}</p><p>Lịch chỉ áp dụng trong khoảng ngày của lớp này. Kế hoạch năm tiếp theo được tạo riêng.</p></div>}
  {sessions.length?<div className="admin-table-scroll"><table className="admin-table"><thead><tr><th>Nội dung buổi học</th><th>Bắt đầu</th><th>Kết thúc</th><th>Phòng học</th><th>Trạng thái</th>{editable&&<th>Thao tác</th>}</tr></thead><tbody>{sessions.map(session=><tr key={session.id}><td>{session.title}</td><td>{display(session.startsAt,'startsAt')}</td><td>{display(session.endsAt,'endsAt')}</td><td>{session.room||record.classroomName||'—'}</td><td>{session.attendanceFinalized?'Đã chốt điểm danh':'Đã xếp lịch'}</td>{editable&&<td>{open&&!session.attendanceFinalized?<button type="button" className="admin-text-button" disabled={busy} onClick={()=>onEdit(session)}>Sửa lịch</button>:<span className="admin-muted">Chỉ xem</span>}</td>}</tr>)}</tbody></table></div>:<p className="admin-muted">{record.planFixed?'Kế hoạch lớp chưa có buổi học được ghi nhận. Liên hệ Admin để kiểm tra.':'Lớp chưa có lịch học. Admin có thể bổ sung buổi học cho lớp chưa có kế hoạch cố định.'}</p>}
 </section>;
}
