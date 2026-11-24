import {useEffect, useState} from 'react';
import {ArrowRight, CalendarDays, CheckCircle2, ChevronLeft, ChevronRight, Clock3, ListChecks} from 'lucide-react';
import {api} from '../../api';
import {AdminLoading, AdminPage, AdminPagination} from '../../admin/AdminUI';
import {dashboardRoles} from '../../roles';
import {canScreen, screenPath} from '../access';
import {attendanceDayLabel, attendanceFilters, attendanceLocalDay, attendanceSearch, attendanceTime, attendanceWeek, attendanceWeekLabel, shiftAttendanceDay} from '../attendanceSchedule';
import '../attendance-schedule.css';

const attendanceStates = {
 ALL: {label: 'Tất cả buổi', count: 'total'},
 PENDING: {label: 'Cần điểm danh', count: 'pending'},
 UPCOMING: {label: 'Sắp diễn ra', count: 'upcoming'},
 FINALIZED: {label: 'Đã chốt', count: 'finalized'},
 BACKLOG: {label: 'Chưa chốt từ các tuần trước', count: 'backlog'},
};

export default function ListAttendance({user, functions = [], onNavigate, onLogout}) {
 const filters = attendanceFilters(new URLSearchParams(window.location.search));
 const requestQuery = attendanceSearch(filters).toString();
 const [data, setData] = useState(null);
 const [loading, setLoading] = useState(true);
 const [error, setError] = useState('');
 const [revision, setRevision] = useState(0);
 const today = data?.today || attendanceLocalDay();
 const isBacklog = filters.status === 'BACKLOG';
 const canOpen = canScreen('ClassAttendance', user, functions);

 useEffect(() => {
  const controller = new AbortController();
  setLoading(true);
  setError('');
  api(`/api/workspace/attendance?${requestQuery}`, {signal: controller.signal}).then(result => {
   if (!controller.signal.aborted) setData(result);
  }).catch(cause => {
   if (controller.signal.aborted) return;
   if (cause.status === 401) onLogout();
   else setError(cause.message);
  }).finally(() => {
   if (!controller.signal.aborted) setLoading(false);
  });
  return () => controller.abort();
 }, [requestQuery, revision, onLogout]);

 function updateFilters(changes) {
  const params = attendanceSearch({...filters, page: 0, ...changes});
  onNavigate(`${screenPath('ListAttendance')}?${params}`, {replace: true});
 }

 function selectWeek(day) {
  const week = attendanceWeek(day);
  if (week) updateFilters({week, status: isBacklog ? 'ALL' : filters.status});
 }

 function openSession(session) {
  const params = attendanceSearch(filters);
  params.set('returnAttendance', '1');
  onNavigate(`${screenPath('ClassAttendance', session.id)}?${params}`);
 }

 const days = new Map();
 for (const session of data?.items || []) {
  const day = attendanceLocalDay(session.startsAt);
  if (!days.has(day)) days.set(day, []);
  days.get(day).push(session);
 }

 return <AdminPage title="Danh sách buổi điểm danh"
  description="Chọn lớp và tuần học để mở nhanh buổi cần điểm danh."
  eyebrow={dashboardRoles[user.role]?.category} code="ListAttendance">
  <section className="admin-card attendance-schedule" aria-labelledby="attendance-schedule-title">
   <div className="attendance-filters">
    <label className="attendance-class-filter">Lớp học
     <select value={filters.classId} onChange={event => updateFilters({classId: event.target.value})}>
      <option value="">Tất cả lớp</option>
      {filters.classId && !data?.classes?.some(entry => entry.id === filters.classId) && <option value={filters.classId}>Lớp đã chọn</option>}
      {(data?.classes || []).map(entry => <option value={entry.id} key={entry.id}>{entry.code} · {entry.name}</option>)}
     </select>
    </label>
    <div className="attendance-week-filter">
     <div className="attendance-week-dates">
      <CalendarDays size={16} aria-hidden="true"/>
      <label>Chọn ngày trong tuần<input type="date" min="1000-01-01" max="9998-12-31" value={filters.week} onChange={event => selectWeek(event.target.value)}/></label>
     </div>
     <div className="attendance-week-nav" aria-label="Chọn tuần học">
      <button type="button" className="admin-secondary" aria-label="Tuần trước" onClick={() => selectWeek(shiftAttendanceDay(filters.week, -7))}><ChevronLeft size={17}/></button>
      <button type="button" className="admin-secondary" onClick={() => updateFilters({week: attendanceWeek(today), status: 'ALL'})}>Tuần này</button>
      <button type="button" className="admin-secondary" aria-label="Tuần sau" onClick={() => selectWeek(shiftAttendanceDay(filters.week, 7))}><ChevronRight size={17}/></button>
     </div>
    </div>
   </div>
   <div className="attendance-states" role="group" aria-label="Trạng thái điểm danh">
    {Object.entries(attendanceStates).map(([status, config]) => <button type="button" key={status} className="admin-secondary"
     aria-pressed={status === filters.status} onClick={() => updateFilters({status})}>
     {config.label}{!loading && !error && data?.counts && <span>{data.counts[config.count]}</span>}
    </button>)}
   </div>
   <header className="attendance-period">
    <h2 id="attendance-schedule-title">{isBacklog ? 'Chưa chốt từ các tuần trước' : `Tuần ${attendanceWeekLabel(filters.week)}`}</h2>
    {!loading && !error && data && <span>{data.total} buổi</span>}
   </header>
   {loading || error ? <AdminLoading error={error} onRetry={() => setRevision(value => value + 1)}/>
    : days.size === 0 ? <div className="admin-empty">{isBacklog ? 'Không có buổi tồn đọng.' : 'Không có buổi học phù hợp trong tuần này.'}</div>
    : <div className="attendance-days">
     {Array.from(days, ([day, sessions]) => <section className="attendance-day" key={day}>
      <h3>{attendanceDayLabel(day)}{day === today && <span>Hôm nay</span>}</h3>
      <ul>{sessions.map(session => {
       const state = session.attendanceStatus;
       const action = user.role === 'LECTURER' && state === 'PENDING' ? 'Điểm danh' : 'Xem buổi';
       return <li key={session.id} className={`attendance-session is-${state.toLowerCase()}`}>
        <time dateTime={session.startsAt}>{attendanceTime(session.startsAt)} – {attendanceTime(session.endsAt)}</time>
        <div className="attendance-session-title"><strong>{session.title}</strong><span>{session.classCode} · {session.className}{session.room ? ` · ${session.room}` : ''}</span></div>
        <span className="attendance-state">{state === 'FINALIZED' ? <CheckCircle2 size={14}/> : state === 'PENDING' ? <ListChecks size={14}/> : <Clock3 size={14}/>} {attendanceStates[state].label}</span>
        {canOpen && <button type="button" className={action === 'Điểm danh' ? '' : 'admin-secondary'} onClick={() => openSession(session)}>{action}<ArrowRight size={14}/></button>}
       </li>;
      })}</ul>
     </section>)}
    </div>}
   {!loading && !error && data && (data.totalPages > 1 || data.page > 0) && <AdminPagination page={data.page} total={data.total} size={data.size} totalPages={data.totalPages} onPage={page => updateFilters({page})}/>}
  </section>
 </AdminPage>;
}
