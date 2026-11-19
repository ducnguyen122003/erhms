import {dashboardPath,firstAllowedPath,resolveRole,workspaceRoute} from './roles.js';
import {screenNavigation,screenPath,screenRoute,screens} from './screens/access.js';

const origin='https://workspace.invalid';
const forbiddenSections=new Set(['unsupported','redirect','forbidden','missing']);
const sectionLabels={dashboard:'Dashboard',users:'Tài khoản', 'user-create':'Thêm tài khoản', 'user-detail':'Chi tiết tài khoản', 'user-edit':'Chỉnh sửa tài khoản',permissions:'Vai trò & phân quyền',settings:'Cấu hình hệ thống',audit:'Nhật ký hệ thống',profile:'Thông tin tài khoản',recruitment:'Tuyển dụng'};
const sameRecordParents={AdmissionConsultation:'ListAdmissionApplicationDetail',EditStudentProfile:'StudentProfileDetail',EditTrainingContract:'TrainingContractDetail',ContractSignature:'TrainingContractDetail',EditCourse:'CourseDetail',EditClass:'ClassDetail',Attendance_Correction:'ClassAttendance',AssetHandover:'EquipmentRequestDetail_IT',EditAsset:'AssetDetail',AssetMaintenance:'AssetDetail',PaymentInstruction:'TuitionDetail',RecordWithdrawal:'TrainingClosureDetail_Withdrawal',ClassGradebook:'ClassDetail_Lecturer'};
const listKinds=new Set(['list','notifications','timetable','report','results','student_attendance','student_accounts','master_list']);

/** History may only supply same-origin workspace locations, never arbitrary URLs. */
export function breadcrumbLocation(href){
 if(typeof href!=='string'||!href.startsWith('/')||href.startsWith('//')||/[\\\u0000-\u0020]/.test(href))return null;
 try{const url=new URL(href,origin);if(url.origin!==origin)return null;return (url.pathname.replace(/\/$/,'')||'/')+url.search+url.hash;}catch{return null;}
}
const pathname=href=>new URL(href,origin).pathname;
function permitted(href,user,functions){
 if(!href||!resolveRole(user))return false;
 const path=pathname(href);
 if(path.startsWith('/admin/')&&user.role!=='ADMIN')return false;
 return !forbiddenSections.has(workspaceRoute(path,user,functions));
}
function item(href,user,functions){
 const path=pathname(href),section=workspaceRoute(path,user,functions),route=screenRoute(path);
 let label=sectionLabels[section];
 if(section==='screen')label=route.screen.code==='AdmissionContractSigning'&&route.id?'Chi tiết hợp đồng cần ký':route.screen.title;
 if(!permitted(href,user,functions)&&!forbiddenSections.has(section))label='Không có quyền truy cập';
 if(!label)label=section==='unsupported'?'Vai trò không hợp lệ':section==='forbidden'?'Không có quyền truy cập':section==='missing'?'Không tìm thấy trang':'Không gian làm việc';
 return {href,label};
}

export function breadcrumbHome(user,functions=[]){
 if(!resolveRole(user))return null;
 const candidates=[dashboardPath(user),firstAllowedPath(user,functions),...screenNavigation(user,functions).map(entry=>entry.path),'/profile','/recruitment'];
 return candidates.find(href=>permitted(href,user,functions))||null;
}

function ancestor(href,user,functions){
 const path=pathname(href),url=new URL(href,origin),route=screenRoute(path);
 if(/^\/admin\/users\/[1-9][0-9]*\/edit$/.test(path))return path.replace(/\/edit$/,'/view');
 if(path==='/admin/users/new'||/^\/admin\/users\/[1-9][0-9]*\/view$/.test(path))return '/admin/users';
 if(!route)return null;
 const {screen,id}=route,code=screen.code;
 if(code==='ListAdmissionApplicationDetail'){
  const from=url.searchParams.get('from');
  if(from==='ListAdmissionEnrollment'){
   const parent=screenPath(from)+'?tab='+(url.searchParams.get('tab')==='forwarded'?'forwarded':'waiting');
   if(permitted(parent,user,functions))return parent;
  }
 }
 if(code==='TimeTable_EditSession'){
  const record=url.searchParams.get('returnClassId');
  if(record){const parent=screenPath('ClassDetail',record);if(permitted(parent,user,functions))return parent;}
 }
 if(id&&listKinds.has(screen.kind)&&!screen.back)return screenPath(code);
 if(screen.kind==='master_add'||screen.kind==='master_edit')return screenPath('List'+screen.masterSuffix);
 let back=sameRecordParents[code]||screen.back;
 if(!back)return null;
 if(code==='PaymentInstruction')back=user.role==='STUDENT'?'TuitionDetail_Student':user.role==='HR'?'PaymentStatus_Admissions':back;
 if(id&&(sameRecordParents[code]===back||code==='PaymentInstruction')){
  let parent=screenPath(back,id);
  if(back==='ListAdmissionApplicationDetail'&&url.search)parent+=url.search;
  if(permitted(parent,user,functions))return parent;
 }
 // Some .back targets refer to another record type (loan -> request, contract ->
 // student). Without its ID, retain only a safe list parent instead of guessing.
 const visited=new Set();
 while(back&&!visited.has(back)){
  visited.add(back);const spec=screens[back];if(!spec)return null;
  const parent=screenPath(back);
  if((listKinds.has(spec.kind)||['StudentAssets','StudentProfileDetail_Student'].includes(back))&&permitted(parent,user,functions))return parent;
  back=spec.back;
 }
 return null;
}

function withHome(trail,user,functions){
 const home=breadcrumbHome(user,functions);
 if(home&&!trail.some(entry=>pathname(entry.href)===pathname(home)))trail.unshift(item(home,user,functions));
 return trail.length>24?[trail[0],...trail.slice(-23)]:trail;
}

export function initialBreadcrumbTrail(href,user,functions=[]){
 const location=breadcrumbLocation(href);if(!location)return [];
 if(!permitted(location,user,functions))return withHome([item(location,user,functions)],user,functions);
 const trail=[],seen=new Set();let current=location;
 while(current&&!seen.has(pathname(current))&&trail.length<12){
  seen.add(pathname(current));if(permitted(current,user,functions))trail.unshift(item(current,user,functions));
  current=ancestor(current,user,functions);
 }
 return withHome(trail,user,functions);
}

/** Rebuild labels and grants instead of trusting serialized browser history. */
export function restoreBreadcrumbTrail(candidate,href,user,functions=[]){
 const location=breadcrumbLocation(href);
 if(!location||!permitted(location,user,functions)||!Array.isArray(candidate))return initialBreadcrumbTrail(href,user,functions);
 const safe=[];
 for(const entry of candidate.slice(-48)){
  const address=breadcrumbLocation(entry?.href);if(!permitted(address,user,functions))continue;
  const prior=safe.findIndex(value=>pathname(value.href)===pathname(address));
  if(prior>=0)safe.splice(prior);
  safe.push(item(address,user,functions));
 }
 const current=safe.findIndex(entry=>pathname(entry.href)===pathname(location));
 if(current<0)return initialBreadcrumbTrail(location,user,functions);
 return withHome([...safe.slice(0,current),item(location,user,functions)],user,functions);
}

function topLevel(href,user,functions){
 const path=pathname(href),route=screenRoute(path);
 return path===breadcrumbHome(user,functions)||['/profile','/recruitment','/admin/users','/admin/permissions','/admin/settings','/admin/audit-logs'].includes(path)||Boolean(route&&!route.id&&listKinds.has(route.screen.kind));
}

export function updateBreadcrumbTrail(trail,target,user,functions=[],{replace=false,trailIndex=null}={}){
 const location=breadcrumbLocation(target);
 if(!location)return Array.isArray(trail)&&trail.length?restoreBreadcrumbTrail(trail,trail.at(-1)?.href,user,functions):[];
 if(!permitted(location,user,functions))return initialBreadcrumbTrail(location,user,functions);
 const previous=(Array.isArray(trail)&&trail.length?restoreBreadcrumbTrail(trail,trail.at(-1)?.href,user,functions):[]).filter(entry=>permitted(entry.href,user,functions));
 if(Number.isInteger(trailIndex)&&trailIndex>=0&&trailIndex<previous.length&&pathname(previous[trailIndex].href)===pathname(location))return withHome([...previous.slice(0,trailIndex),item(location,user,functions)],user,functions);
 const existing=previous.findIndex(entry=>pathname(entry.href)===pathname(location));
 if(existing>=0)return withHome([...previous.slice(0,existing),item(location,user,functions)],user,functions);
 if(topLevel(location,user,functions)||previous.length<=1)return initialBreadcrumbTrail(location,user,functions);
 const last=previous.at(-1),from=screenRoute(pathname(last.href)),to=screenRoute(pathname(location));
 const sibling=from&&to&&from.id&&to.id&&from.screen.code===to.screen.code;
 return withHome([...(replace||sibling?previous.slice(0,-1):previous),item(location,user,functions)],user,functions);
}
