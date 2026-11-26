export const weekdayChoices=[{id:1,name:'Thứ Hai'},{id:2,name:'Thứ Ba'},{id:3,name:'Thứ Tư'},{id:4,name:'Thứ Năm'},{id:5,name:'Thứ Sáu'},{id:6,name:'Thứ Bảy'},{id:7,name:'Chủ nhật'}];

export function classPlanYear(record={}){return record.planYear||(/^\d{4}-\d{2}-\d{2}$/.test(record.startsOn||'')?Number(record.startsOn.slice(0,4)):null);}
export function classPlanYears(classes=[]){return [...new Set(classes.map(classPlanYear).filter(Boolean))].sort((a,b)=>b-a);}
export function weeklyPlanLabel(record={}){
 const raw=Array.isArray(record.weekdays)?record.weekdays:[];
 const days=weekdayChoices.filter(day=>raw.map(Number).includes(day.id)).map(day=>day.name).join(', ');
 return [days,record.startTime&&record.endTime?record.startTime.slice(0,5)+' – '+record.endTime.slice(0,5):''].filter(Boolean).join(' · ');
}

function calendarDate(value){
 if(!/^\d{4}-\d{2}-\d{2}$/.test(value||''))return null;
 const date=new Date(value+'T12:00:00Z');
 return Number.isNaN(date.valueOf())||date.toISOString().slice(0,10)!==value?null:date;
}

export function classPlanDefaultEnd(startsOn,durationMonths){
 const start=calendarDate(startsOn),months=Number(durationMonths);
 if(!start||!Number.isInteger(months)||months<1||months>24)return '';
 const end=new Date(start);end.setUTCDate(1);end.setUTCMonth(end.getUTCMonth()+months);
 const lastDay=new Date(end);lastDay.setUTCMonth(lastDay.getUTCMonth()+1);lastDay.setUTCDate(0);
 end.setUTCDate(Math.min(start.getUTCDate(),lastDay.getUTCDate()));
 return end.toISOString().slice(0,10);
}

export function changeClassPlanValue(values,key,value,options={}){
 const next={...values,[key]:value};
 const previous=options.courses?.find(course=>String(course.id)===String(values.courseId));
 const course=options.courses?.find(course=>String(course.id)===String(next.courseId));
 if(key==='courseId'){
  next.capacity=course?.classCapacity??'';
  if(!values.name||values.name===previous?.name)next.name=course?.name||'';
 }
 if(key==='startsOn'||key==='courseId'){
  const previousDefault=classPlanDefaultEnd(values.startsOn,previous?.durationMonths),suggested=classPlanDefaultEnd(next.startsOn,course?.durationMonths);
  if(suggested&&(!calendarDate(values.endsOn)||values.endsOn===previousDefault||values.endsOn<next.startsOn))next.endsOn=suggested;
  else if(!next.startsOn&&values.endsOn===previousDefault)next.endsOn='';
 }
 return next;
}

export function classPlanPreview(values={}){
 const start=/^\d{4}-\d{2}-\d{2}$/.test(values.startsOn||'')?new Date(values.startsOn+'T12:00:00Z'):null;
 const end=/^\d{4}-\d{2}-\d{2}$/.test(values.endsOn||'')?new Date(values.endsOn+'T12:00:00Z'):null;
 if(!start||!end||Number.isNaN(start.valueOf())||Number.isNaN(end.valueOf())||start>end||end-start>366*86400000||Number(values.planYear)!==start.getUTCFullYear())return null;
 if(start.toISOString().slice(0,10)!==values.startsOn||end.toISOString().slice(0,10)!==values.endsOn)return null;
 const days=new Set((values.weekdays||[]).map(Number).filter(day=>day>=1&&day<=7));
 if(!days.size||!/^(?:[01]\d|2[0-3]):[0-5]\d$/.test(values.startTime||'')||!/^(?:[01]\d|2[0-3]):[0-5]\d$/.test(values.endTime||'')||values.endTime<=values.startTime)return null;
 const dates=[],presentDays=new Set();
 for(let cursor=new Date(start);cursor<=end;cursor.setUTCDate(cursor.getUTCDate()+1)){
  const weekday=cursor.getUTCDay()||7;
  if(days.has(weekday)){dates.push(cursor.toISOString().slice(0,10));presentDays.add(weekday);}
 }
 return {count:dates.length,first:dates[0]||null,last:dates.at(-1)||null,missingWeekdays:weekdayChoices.filter(day=>days.has(day.id)&&!presentDays.has(day.id)).map(day=>day.id)};
}

export function contractClassChoices(values={},options={}){
 const admission=options.admissions?.find(row=>String(row.id)===String(values.consultationId));
 const courseId=String(values.courseId??'');
 if(!admission||!courseId)return [];
 const normalized=value=>String(value||'').normalize('NFC').trim().replace(/\s+/gu,' ').toLocaleLowerCase('vi-VN');
 const registered=normalized(admission?.enrollmentCourse);
 return (options.classes||[]).filter(row=>{
  if(String(row.courseId??'')!==courseId)return false;
  if(!['PLANNED','ACTIVE'].includes(row.status))return false;
  if(row.remainingSlots!==undefined&&Number(row.remainingSlots)<=0&&String(row.id)!==String(values.classId))return false;
  const course=options.courses?.find(course=>String(course.id)===String(row.courseId));
  if(registered)return normalized(row.courseName||course?.name)===registered;
  return true;
 });
}
