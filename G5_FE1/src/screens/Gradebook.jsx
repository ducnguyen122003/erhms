import {useEffect,useState} from 'react';
import {ArrowLeft} from 'lucide-react';
import {display} from './definitions';
import {useUnsavedChanges} from './ScreenForm';
import './gradebook.css';

const canEnter=student=>student.status==='ACTIVE'&&!student.closedAt&&!student.academicCompletedAt&&!student.academicFailedAt;
const scoreKey=(enrollmentId,title)=>enrollmentId+':'+title;
const hasValidScore=value=>{
 if(value==='')return false;
 const score=Number(value);
 return Number.isFinite(score)&&score>=0&&score<=10&&Math.round(score*100)/100===score;
};
const toRecord=({enrollmentId,title,score})=>({enrollmentId,title,score:score===''?null:Number(score)});

export default function Gradebook({record,related,editable,busy,onBack,onReload,onSave}){
 const [edits,setEdits]=useState(()=>new Map());
 const [accepted,setAccepted]=useState(null);
 const dirty=useUnsavedChanges();
 const assessments=related.assessments||[],students=related.students||[];
 const gradeable=students.filter(canEnter);
 const gradeableIds=new Set(gradeable.map(student=>student.enrollmentId));
 const activeTitles=new Set(assessments.map(assessment=>assessment.title));
 const studentNames=new Map(students.map(student=>[student.enrollmentId,student.studentName]));
 const grades=new Map();
 for(const grade of related.grades||[]){
  const key=scoreKey(grade.enrollmentId,grade.title);
  if(!grades.has(key))grades.set(key,grade);
 }
 if(accepted&&accepted.version===record.version){
  for(const [key,grade] of accepted.grades)grades.set(key,grade);
 }
 const score=(key)=>{
  const grade=grades.get(key),draft=edits.get(key);
  if(!grade?.publishedAt&&editable&&gradeableIds.has(draft?.enrollmentId))return draft.score;
  return grade?.score??'';
 };
 const drafts=[...edits].filter(([key,grade])=>editable&&gradeableIds.has(grade.enrollmentId)&&activeTitles.has(grade.title)&&!grades.get(key)?.publishedAt);
 const invalidDraft=drafts.some(([,grade])=>grade.score!==''&&!hasValidScore(grade.score));
 const hasDrafts=drafts.length>0;
 const historical=[...grades.values()].filter(grade=>grade.score!=null&&!activeTitles.has(grade.title));
 useEffect(()=>{dirty.current=hasDrafts;},[dirty,hasDrafts]);

 function change(enrollmentId,title,value){
  const key=scoreKey(enrollmentId,title);
  const previous=grades.get(key)?.score??'';
  const unchanged=value===String(previous)||(hasValidScore(value)&&hasValidScore(previous)&&Number(value)===Number(previous));
  setEdits(current=>{
   const next=new Map(current);
   if(unchanged)next.delete(key);
   else next.set(key,{enrollmentId,title,score:value});
   return next;
  });
 }

 function submit(assessmentTitle,changed){
  const records=changed.map(([,grade])=>toRecord(grade));
  const values={version:record.version,records};
  const saved=new Map(records.map(grade=>[scoreKey(grade.enrollmentId,grade.title),grade]));
  if(assessmentTitle){
   values.assessmentTitle=assessmentTitle;
   for(const student of gradeable){
    const key=scoreKey(student.enrollmentId,assessmentTitle);
    if(!grades.get(key)?.publishedAt){
     saved.set(key,{enrollmentId:student.enrollmentId,title:assessmentTitle,score:Number(score(key)),publishedAt:true});
    }
   }
  }
  onSave(values,()=>{
   setAccepted({version:record.version,grades:saved});
   setEdits(current=>{
    const next=new Map(current);
    for(const [key] of changed)next.delete(key);
    return next;
   });
  },assessmentTitle?'publish':'save');
 }

 function publish(title){
  if(window.confirm(`Công bố “${title}” cho cả lớp? Điểm của thành phần này sẽ được khóa.`)){
   submit(title,drafts.filter(([,grade])=>grade.title===title));
  }
 }

 return <section className="admin-card screen-detail">
  <header className="screen-section-header">
   <h2>Bảng điểm lớp</h2>
   <button type="button" className="admin-text-button" onClick={onBack}><ArrowLeft size={14} aria-hidden="true"/>Danh sách lớp</button>
  </header>
  <p className="admin-muted">Thang điểm 10. Học viên xem được điểm từng thành phần sau khi giảng viên công bố.</p>
  {onReload&&<p className="gradebook-reload"><button type="button" className="admin-secondary" onClick={onReload}>Tải lại bảng điểm</button></p>}
  {!assessments.length?<p className="admin-message">Khóa học chưa có đầu điểm.</p>:<div className="admin-table-scroll">
   <table className="admin-table gradebook-scores" aria-busy={busy&&!onReload}>
    <thead><tr>
     <th scope="col">Học viên</th>
     {assessments.map(({title,weight})=>{
      const keys=gradeable.map(student=>scoreKey(student.enrollmentId,title));
      const missing=keys.filter(key=>!hasValidScore(score(key))).length;
      const published=keys.length>0&&keys.every(key=>grades.get(key)?.publishedAt);
      return <th scope="col" key={title}>
       {title}<small> {weight}%</small>
       {editable&&keys.length>0&&<div className="gradebook-assessment-actions">
        {published?<span className="admin-badge">Đã công bố</span>:<>
         <button type="button" className="admin-secondary" aria-label={'Công bố điểm '+title} disabled={busy||missing>0} onClick={()=>publish(title)}>Công bố điểm</button>
         {missing>0&&<span>Thiếu điểm hợp lệ của {missing} học viên</span>}
        </>}
       </div>}
      </th>;
     })}
     <th scope="col">Điểm tổng</th>
    </tr></thead>
    <tbody>{students.map(student=>{
     const {enrollmentId,studentName}=student;
     const complete=assessments.every(({title})=>hasValidScore(score(scoreKey(enrollmentId,title))));
     const total=complete?assessments.reduce((sum,{title,weight})=>sum+Number(score(scoreKey(enrollmentId,title)))*Number(weight)/100,0):null;
     return <tr key={enrollmentId}>
      <td>{studentName}</td>
      {assessments.map(({title})=>{
       const key=scoreKey(enrollmentId,title),value=score(key);
       const published=grades.get(key)?.publishedAt;
       return <td key={title}>{published?<div className="gradebook-published-score">
        <span>{value===''?'—':value}</span><small>Đã công bố</small>
       </div>:editable&&canEnter(student)
        ?<input type="number" min="0" max="10" step="0.01" aria-label={title+' · '+studentName} aria-invalid={value!==''&&!hasValidScore(value)} value={value} disabled={busy} onChange={event=>change(enrollmentId,title,event.target.value)}/>
        :value===''?'—':value}</td>;
      })}
      <td>{total===null?'Chưa đủ điểm':total.toLocaleString('vi-VN',{minimumFractionDigits:2,maximumFractionDigits:4})}</td>
     </tr>;
    })}</tbody>
   </table>
  </div>}
  {!students.length&&<p className="admin-empty">Lớp chưa có học viên.</p>}
  {editable&&students.length>0&&!gradeable.length&&<p className="admin-muted">Lớp hiện chưa có học viên có thể nhập điểm.</p>}
  {editable&&gradeable.length>0&&assessments.length>0&&<footer className="screen-actions">
   {invalidDraft&&<p role="status" className="admin-muted">Điểm phải từ 0 đến 10 và có tối đa 2 chữ số thập phân.</p>}
   <button type="button" className="admin-secondary" disabled={busy||!hasDrafts||invalidDraft} onClick={()=>submit(null,drafts)}>Lưu bảng điểm</button>
  </footer>}
  {historical.length>0&&<section className="screen-related">
   <h3>Điểm theo cấu hình trước</h3>
   <div className="admin-table-scroll"><table className="admin-table">
    <thead><tr><th scope="col">Học viên</th><th scope="col">Đầu điểm</th><th scope="col">Điểm</th><th scope="col">Ngày công bố</th></tr></thead>
    <tbody>{historical.map(grade=><tr key={grade.id}><td>{studentNames.get(grade.enrollmentId)||'—'}</td><td>{grade.title}</td><td>{grade.score}</td><td>{grade.publishedAt?display(grade.publishedAt,'publishedAt'):'Chưa công bố'}</td></tr>)}</tbody>
   </table></div>
  </section>}
 </section>;
}
