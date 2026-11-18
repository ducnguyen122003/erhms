export const admissionDashboardCodes=['ListAdmissionApplication','ListAdmissionEnrollment'];
export const enrollmentLabels={NO_CONTRACT:'Chờ lập hợp đồng',WAIT_SIGNATURE:'Chờ ký hợp đồng',SIGNED:'Đã ký hợp đồng',WAIT_CONTRACT:'Chờ lập hợp đồng',WAIT_ACCOUNT:'Chờ cấp tài khoản',ACCOUNT_CREATED:'Đã cấp tài khoản',WAIT_HANDOVER:'Chờ bàn giao hợp đồng',UNPAID:'Chưa đóng học phí',PARTIALLY_PAID:'Đã đóng một phần',PAID:'Đã hoàn tất học phí'};
export function admissionHome(record={}){return record.forwardedAt||record.documentStatus==='DOCUMENTS_COMPLETE'?'ListAdmissionEnrollment':'ListAdmissionApplication';}
export function admissionEnrollmentTab(search=''){return new URLSearchParams(search).get('tab')==='forwarded'?'forwarded':'waiting';}
export function admissionMenuActive(currentCode,menuCode,search=''){
 if(!admissionDashboardCodes.includes(menuCode))return false;
 if(admissionDashboardCodes.includes(currentCode))return currentCode===menuCode;
 if(['ListAdmissionApplicationDetail','AdmissionConsultation'].includes(currentCode)){
  const source=new URLSearchParams(search).get('from');
  return (admissionDashboardCodes.includes(source)?source:'ListAdmissionApplication')===menuCode;
 }
 return false;
}
