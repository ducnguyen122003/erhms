import {useEffect,useRef,useState} from 'react';
import {ArrowUpRight,CalendarDays,Plus,RefreshCw} from 'lucide-react';
import {api} from '../api';
import {AdminLoading,AdminMessage,AdminPage,AdminPagination} from '../admin/AdminUI';
import {canScreen,screenPath,screens} from './access';
import {display} from './definitions';
import {weeklyPlanLabel} from './classPlanning';
import './screens.css';
import './course-schedules.css';

export default function CourseSchedulePlans({user,functions=[],onNavigate,onLogout}){
 const initial=useRef(new URLSearchParams(window.location.search));
 const [courseId,setCourseId]=useState(initial.current.get('courseId')||''),[year,setYear]=useState(initial.current.get('year')||''),[page,setPage]=useState(0),[data,setData]=useState(null),[loading,setLoading]=useState(true),[error,setError]=useState(''),[revision,setRevision]=useState(0);
 const can=code=>canScreen(code,user,functions);
 useEffect(()=>{
  const controller=new AbortController();setLoading(true);setError('');
  const params=new URLSearchParams({year,page:String(page),...(courseId?{courseId}:{})});
  api('/api/workspace/screens/CourseSchedules?'+params,{signal:controller.signal}).then(result=>{
   if(controller.signal.aborted)return;
   setData(result);
   const courses=result.courses||result.options?.courses||[];
   if(!courseId&&courses.length)setCourseId(courses[0].id);
  }).catch(cause=>{if(!controller.signal.aborted){if(cause.status===401)onLogout?.();else setError(cause.message);}}).finally(()=>{if(!controller.signal.aborted)setLoading(false);});
  return()=>controller.abort();
 },[courseId,year,page,revision]);
 const courses=data?.courses||data?.options?.courses||[],selected=courses.find(course=>String(course.id)===String(courseId)),plans=(data?.items||data?.plans||[]).filter(plan=>!courseId||String(plan.courseId)===String(courseId));
 const create=()=>onNavigate(screenPath('AddClass')+'?'+new URLSearchParams({courseId,year}));
 return <AdminPage title={screens.CourseSchedules?.title||'Cấu hình lịch học theo khóa'} description="Chọn một trong bốn khóa đào tạo để lập lịch, phân công giảng viên và quản lý kế hoạch từng năm." eyebrow="QUẢN TRỊ ĐÀO TẠO" code="CourseSchedules">
  <AdminMessage error={error}/>
  {loading&&!data?<AdminLoading/>:error&&!data?<AdminLoading error={error} onRetry={()=>setRevision(value=>value+1)}/>:<>
   <section className="fixed-course-cards" aria-label="Bốn khóa đào tạo cố định">{courses.map(course=><button type="button" key={course.id} className={'admin-card fixed-course-card'+(String(course.id)===String(courseId)?' is-selected':'')} aria-pressed={String(course.id)===String(courseId)} onClick={()=>{setCourseId(course.id);setPage(0);}}><CalendarDays size={20}/><strong>{course.name}</strong><span>{course.durationMonths} tháng</span><b>{display(course.tuitionFee,'tuitionFee')}</b></button>)}</section>
   <section className="admin-card course-schedule-plans" aria-busy={loading}>
    <header><div><span className="eyebrow">KẾ HOẠCH LỚP THEO KHÓA</span><h2>{selected?.name||'Chọn khóa đào tạo'}</h2><p>Mỗi kế hoạch có ngày bắt đầu, ngày kết thúc, giảng viên, phòng và lịch học cố định riêng.</p></div><div className="screen-actions"><button type="button" className="admin-secondary" disabled={loading} onClick={()=>setRevision(value=>value+1)}><RefreshCw size={15}/>Tải lại</button>{can('AddClass')&&<button type="button" disabled={!selected||loading} onClick={create}><Plus size={15}/>Tạo lịch học cho khóa</button>}</div></header>
    <div className="course-schedule-filters"><label className="admin-field">Năm kế hoạch<select value={year} onChange={event=>{setYear(event.target.value);setPage(0);}}><option value="">Tất cả năm kế hoạch</option>{(data?.options?.planYears||[]).map(planYear=><option key={planYear} value={planYear}>{planYear}</option>)}</select></label><p className="admin-muted">Lịch được tạo trong khoảng ngày của lớp. Năm tiếp theo lập kế hoạch riêng.</p></div>
    {data?.options?.managers?.length===0&&<p className="course-schedule-setup-note">Chưa có tài khoản quản lý học viên đang hoạt động. Tạo hoặc kích hoạt tài khoản QLHV trong phần Tài khoản người dùng trước khi lưu lịch học.</p>}
    {data?.options?.lecturers?.length===0&&<p className="course-schedule-setup-note">Chưa có tài khoản giảng viên đang hoạt động. Tạo hoặc kích hoạt tài khoản giảng viên trước khi phân công lịch học.</p>}
    {!plans.length?<div className="admin-empty"><CalendarDays size={24}/><p>{loading?'Đang tải kế hoạch…':'Khóa học chưa có kế hoạch phù hợp trong năm đã chọn.'}</p>{selected&&can('AddClass')&&<button type="button" className="admin-secondary" disabled={loading} onClick={create}>Lập kế hoạch đầu tiên</button>}</div>:<div className="admin-table-scroll"><table className="admin-table"><thead><tr><th>Lớp / năm</th><th>Ngày học</th><th>Lịch hằng tuần</th><th>Giảng viên / quản lý</th><th>Sĩ số</th><th>Thao tác</th></tr></thead><tbody>{plans.map(plan=><tr key={plan.id}><td><strong>{plan.code}</strong><small>{plan.name}</small><small>Năm {plan.planYear} · {display(plan.status)}</small></td><td>{display(plan.startsOn,'startsOn')}<small>đến {display(plan.endsOn,'endsOn')}</small></td><td>{weeklyPlanLabel(plan)||'Chưa cập nhật lịch'}<small>{plan.classroomName||'Chưa cập nhật phòng'}</small></td><td>{plan.lecturerName||'Chưa phân công giảng viên'}<small>{plan.managerName||'Chưa phân công quản lý'}</small></td><td>{plan.studentCount||0}/{plan.capacity}<small>Còn nhận: {plan.remainingSlots??'—'}</small></td><td><div className="course-plan-row-actions">{can('ClassDetail')&&<button type="button" className="admin-text-button" onClick={()=>onNavigate(screenPath('ClassDetail',plan.id))}>Xem lịch<ArrowUpRight size={13}/></button>}{can('EditClass')&&['PLANNED','ACTIVE'].includes(plan.status)&&<button type="button" className="admin-text-button" onClick={()=>onNavigate(screenPath('EditClass',plan.id))}>Cấu hình</button>}</div></td></tr>)}</tbody></table></div>}
    <AdminPagination page={data?.page||0} total={data?.total||0} size={25} totalPages={data?.totalPages||0} onPage={setPage}/>
   </section>
  </>}
 </AdminPage>;
}
