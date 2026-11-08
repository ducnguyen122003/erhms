import catalog from './catalog.json' with {type:'json'};

export const screens=Object.fromEntries(catalog.map(screen=>[screen.code,screen]));
const legacy={ListUser:'/admin/users',RolePermission:'/admin/permissions',SystemConfiguration:'/admin/settings',SystemAuditLog:'/admin/audit-logs',UserProfile:'/profile',ChangePassword:'/screens/ChangePassword'};
const dashboards={Dashboard_Admin:'admin',Dashboard_Lecturer:'lecturer',Dashboard_Student:'student',Dashboard_Accountant:'accountant',Dashboard_IT_Support:'it-support',Dashboard_Student_Manager:'manager',Dashboard_Admissions:'hr'};
export function screenPath(code,id){
 if(legacy[code])return legacy[code];
 if(code==='EditUser')return id?'/admin/users/'+id+'/edit':'/admin/users/new';
 if(code==='UserProfile_AdminView')return id?'/admin/users/'+id+'/view':'/admin/users';
 if(dashboards[code])return '/dashboard/'+dashboards[code];
 if(['Login','ForgetPassword','ForgetPassword_Sent','ResetPassword'].includes(code))return '/login';
 return (screens[code]?.public?'/public/':'/screens/')+code+(id?'/'+encodeURIComponent(id):'');
}
export function screenRoute(path){
 const match=/^\/screens\/([A-Za-z0-9_]+)(?:\/([1-9][0-9]*|[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}))?$/.exec(path);
 return match&&screens[match[1]]&&!screens[match[1]].public?{screen:screens[match[1]],id:match[2]||null}:null;
}
export function canScreen(code,user,functions=[]){const s=screens[code];return Boolean(s&&(s.public||(s.roles.includes(user?.role)&&functions.includes(s.functionCode))));}
const menu={
 ADMIN:['ListClassroom','Reports_Admin'],
 HR:['ListAdmissionApplication','ListTrainingContract','ListCourse','PaymentStatus_Admissions','Reports_Admissions'],
 MANAGER:['ListStudent','ListProfession','ListCourse','ListClass','AutoAssignmentReview','TimeTable_Manager','ListAttendance','ListTrainingContract','PaymentStatus_Admissions','ListAssetRecovery','ListTrainingClosure','Reports_Student_Manager'],
 LECTURER:['ListClass_Lecturer','TimeTable_Lecturer','ListAttendance','ListEquipmentRequest','Reports_Lecturer'],
 STUDENT:['StudentProfileDetail_Student','TimeTable_Student','StudentAttendance','StudentResults','TuitionDetail_Student','StudentAssets'],
 ACCOUNTANT:['ListPayment','ListTuitionManagement','DepositList','ListTrainingContract','ListAssetIncident','ListAssetRecovery','ListTrainingClosure','Reports_Accountant'],
 IT_SUPPORT:['ListEquipmentGroup','ListStorageLocation','ListAsset','ListEquipmentRequest_IT','ListAssetRecovery','ListAssetIncident','ListTrainingClosure','Reports_IT_Support']
};
export function screenNavigation(user,functions){return [...(menu[user?.role]||[]),'ListNotification'].filter(code=>canScreen(code,user,functions)).map(code=>({code,label:screens[code].title,path:screenPath(code),fe:screens[code].fe}));}
export function dashboardLink(user,detail,functions){
 const panels={ADMIN:{activity:'SystemAuditLog'},LECTURER:{schedule:'TimeTable_Lecturer',classes:'ClassDetail_Lecturer'},STUDENT:{schedule:'TimeTable_Student',grades:'StudentResults',equipment:'StudentAssetReceipt',loans:'StudentAssetReceipt',payments:'TuitionDetail_Student'},ACCOUNTANT:{transactions:'PaymentDetail',payments:'TuitionDetail'},IT_SUPPORT:{requests:'EquipmentRequestDetail_IT',loans:'ListAssetDetail'},MANAGER:{assignments:'AutoAssignmentDetail',attention:'ListStudent'},HR:{registrations:'ListAdmissionApplicationDetail'}};
 const tasks={ADMIN:{disabled:'ListUser',locked:'ListUser',roles:'RolePermission'},LECTURER:{today:'TimeTable_Lecturer',attendance:'ListAttendance',grades:'ClassGradebook',requests:'ListEquipmentRequest'},STUDENT:{attendance:'StudentAttendance',grades:'StudentResults',tuition:'TuitionDetail_Student',equipment:'StudentAssets'},ACCOUNTANT:{pending:'ListPayment',mismatch:'ListPayment',refunds:'DepositList',compensation:'ListAssetIncident'},IT_SUPPORT:{pending:'ListEquipmentRequest_IT',handovers:'ListEquipmentRequest_IT',returns:'ListAssetRecovery',incidents:'ListAssetIncident'},MANAGER:{assignments:'AutoAssignmentReview',conflicts:'TimeTable_Manager',incomplete:'ListTrainingClosure',withdrawn:'ListTrainingClosure'},HR:{appointments:'ListAdmissionApplication',drafts:'ListTrainingContract',fresh:'ListAdmissionApplication',payments:'PaymentStatus_Admissions'}};
 const code=detail.taskKey?tasks[user.role]?.[detail.taskKey]:panels[user.role]?.[detail.panelKey];if(!code||!canScreen(code,user,functions))return null;
 const key=!detail.taskKey&&!['list','notifications','report','results','student_attendance'].includes(screens[code]?.kind)?detail.row?.id:null;
 return screenPath(code,key);
}
