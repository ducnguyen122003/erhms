export const signingStates=['SENT_FOR_SIGNATURE','SIGNATURE_REVIEW','SIGNED','ACTIVE','COMPLETED'];

export function canSendContract(record={}){return record.status==='DRAFT';}
export function canSubmitSignedScan(record={}){return record.status==='SENT_FOR_SIGNATURE';}
export function canReviewSignedScan(record={}){return record.status==='SIGNATURE_REVIEW'&&Boolean(record.signedScanFileId);}
export function canProvisionContract(record={}){return ['SIGNED','ACTIVE','COMPLETED'].includes(record.status)&&Boolean(record.signatureApprovedAt);}

export function signingReviewFields(fields,values={}){
 const approving=values.decision!=='REJECT';
 return fields.filter(field=>approving||!['accountantId','handoverNote'].includes(field.key)).map(field=>field.key==='reviewNote'?{...field,label:approving?'Ý kiến xác nhận (nếu có)':'Lý do yêu cầu ký / scan lại',required:!approving}:field);
}

export function validateSignedScan(file){
 if(!file||file.size===0)return 'Chọn bản scan PDF có dữ liệu.';
 if(file.size>10*1024*1024)return 'Bản scan không được vượt quá 10 MB.';
 if(file.type!=='application/pdf'&&!/\.pdf$/i.test(file.name||''))return 'Bản scan hợp đồng chỉ nhận file PDF.';
 return '';
}

export function signingHelp(record={}){
 if(record.status==='DRAFT')return 'Hoàn tất thông tin và gửi hợp đồng cho tuyển sinh để in, cho học viên ký.';
 if(record.status==='SENT_FOR_SIGNATURE')return 'Tuyển sinh in PDF đã gửi, cho học viên ký và tải bản scan lên để QLHV xác nhận.';
 if(record.status==='SIGNATURE_REVIEW')return 'Bản scan đã được gửi. QLHV kiểm tra và xác nhận trước khi cấp tài khoản học viên.';
 if(canProvisionContract(record))return record.studentId?'Bản ký đã được QLHV xác nhận và đã gắn tài khoản học viên.':'Bản ký đã được QLHV xác nhận. Có thể cấp tài khoản học viên.';
 return '';
}
