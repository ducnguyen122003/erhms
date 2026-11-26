export const signingStates=['SENT_FOR_SIGNATURE','SIGNATURE_REVIEW','SIGNED','ACTIVE','COMPLETED'];

export const draftSendLimit=20;

export function canSendContract(record={}){
 return record.status==='DRAFT'
  &&!['signatureSentAt','signatureSubmittedAt','signatureApprovedAt','printFileId','signedScanFileId','signedOn','handedOverAt'].some(field=>record[field]!=null)
  &&!String(record.signatureEvidence??'').trim();
}

export function changeDraftSelection(selection,rows,checked){
 const next=new Map(selection);
 for(const row of rows){
  if(!checked)next.delete(row.id);
  else if(canSendContract(row)&&next.size<draftSendLimit)next.set(row.id,row);
 }
 return next;
}

export function refreshDraftSelection(selection,rows){
 const stale=rows.filter(row=>selection.has(row.id)&&(!canSendContract(row)||selection.get(row.id).version!==row.version));
 return stale.length?changeDraftSelection(selection,stale,false):selection;
}

export function canSubmitSignedScan(record={}){return record.status==='SENT_FOR_SIGNATURE';}
export function canReviewSignedScan(record={}){return record.status==='SIGNATURE_REVIEW'&&Boolean(record.signedScanFileId);}
export function canProvisionContract(record={}){return ['SIGNED','ACTIVE','COMPLETED'].includes(record.status)&&Boolean(record.signatureApprovedAt);}

export function signingReviewFields(fields,values={}){
 const approving=values.decision!=='REJECT';
 return fields.filter(field=>approving||field.key!=='handoverNote').map(field=>field.key==='reviewNote'?{...field,label:approving?'Ý kiến xác nhận (nếu có)':'Lý do yêu cầu ký / scan lại',required:!approving}:field);
}

export function signingReviewPayload({version,decision,reviewNote,handoverNote}){
 return {version,decision,reviewNote,...(decision==='APPROVE'?{handoverNote}:{})};
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
