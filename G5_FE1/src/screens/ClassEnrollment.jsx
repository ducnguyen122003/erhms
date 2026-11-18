import {useState} from 'react';
import {Plus,RefreshCw,Search} from 'lucide-react';
import {useUnsavedChanges} from './ScreenForm';
import './class-management.css';

export default function ClassEnrollment({record,students=[],busy,error,onSave,onReload}){
 const [search,setSearch]=useState(''),[selected,setSelected]=useState([]),dirty=useUnsavedChanges();
 const available=Math.max(0,Number(record.capacity)-Number(record.studentCount));
 const query=search.trim().toLocaleLowerCase('vi-VN');
 const visible=students.filter(student=>[student.fullName,student.name,student.email,student.phone].some(value=>String(value||'').toLocaleLowerCase('vi-VN').includes(query)));
 const toggle=(id,checked)=>{dirty.current=true;setSelected(current=>checked?[...current,id]:current.filter(value=>value!==id));};
 async function submit(event){event.preventDefault();if(busy||!selected.length||selected.length>available)return;await onSave({version:record.version,students:selected.map(id=>({id}))},()=>{dirty.current=false;setSelected([]);});}
 return <section className="screen-related class-enrollment" aria-labelledby="class-enrollment-heading">
  <header className="class-schedule-heading"><h3 id="class-enrollment-heading">Thêm học viên chưa được xếp lớp</h3><button type="button" className="admin-text-button" disabled={busy} onClick={()=>{dirty.current=false;setSelected([]);onReload();}}><RefreshCw size={14}/>Tải lại danh sách</button></header>
  <p className="admin-muted">Chọn học viên đã đóng đủ học phí của khóa học này, thuộc phạm vi quản lý và chưa được xếp lớp. Lớp còn <strong>{available}</strong> chỗ.</p>
  {students.length?<form onSubmit={submit} aria-busy={busy}>
   <label className="screen-search class-student-search"><Search size={16}/><input type="search" aria-label="Tìm học viên chưa được xếp lớp" placeholder="Tìm tên, email hoặc số điện thoại…" value={search} onChange={event=>setSearch(event.target.value)} disabled={busy}/></label>
   <div className="admin-table-scroll"><table className="admin-table"><thead><tr><th>Chọn</th><th>Học viên</th><th>Email</th><th>Số điện thoại</th></tr></thead><tbody>{visible.map(student=>{const checked=selected.includes(student.id);return <tr key={student.id} className={checked?'class-student-selected':''}><td><input type="checkbox" aria-label={'Thêm '+(student.fullName||student.name)+' vào lớp'} checked={checked} disabled={busy||(!checked&&selected.length>=available)} onChange={event=>toggle(student.id,event.target.checked)}/></td><td>{student.fullName||student.name}</td><td>{student.email||'—'}</td><td>{student.phone||'—'}</td></tr>;})}</tbody></table></div>
   {!visible.length&&<p className="admin-muted">Không tìm thấy học viên phù hợp.</p>}
   {error&&<p className="field-error" role="alert">{error}</p>}
   <footer className="class-enrollment-footer"><span role="status">Đã chọn {selected.length} / {available} chỗ trống</span><button type="submit" disabled={busy||!selected.length||selected.length>available}><Plus size={15}/>{busy?'Đang thêm…':'Thêm học viên vào lớp'}</button></footer>
  </form>:<p className="admin-muted">Chưa có học viên đã đóng đủ học phí của khóa học này và chưa được xếp lớp. Danh sách được cập nhật sau khi kế toán xác nhận thanh toán.</p>}
 </section>;
}
