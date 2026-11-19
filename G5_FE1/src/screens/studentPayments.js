export const paymentKinds={TUITION:'Học phí',DEPOSIT:'Cọc theo hợp đồng'};
export const submissionStatuses={PENDING:'Chờ kế toán đối chiếu',MISMATCH:'Cần làm rõ',CONFIRMED:'Đã xác nhận nhận tiền'};
export const paymentApi='/api/workspace/student-payments';

export function paymentToday(){const now=new Date();return new Date(now.getTime()-now.getTimezoneOffset()*60000).toISOString().slice(0,10);}
export function paymentAmount(value){const amount=Number(value);return value!==''&&Number.isSafeInteger(amount)&&amount>0?amount:null;}
export function normalizedTransferMemo(value){return String(value||'').trim().replace(/\s+/g,' ').toUpperCase();}
export function validPaymentDate(value,today=paymentToday()){
 if(!/^\d{4}-\d{2}-\d{2}$/.test(value||'')||value>today)return false;
 const parsed=new Date(value+'T00:00:00Z');return !Number.isNaN(parsed.valueOf())&&parsed.toISOString().slice(0,10)===value;
}
export function validatePaymentBill(file){
 if(!file||file.size===0)return 'Chọn bill chuyển khoản có dữ liệu.';
 if(file.size>2*1024*1024)return 'Bill chuyển khoản không được vượt quá 2 MB.';
 if(!['application/pdf','image/png','image/jpeg'].includes(file.type)&&!(!file.type&&/\.(pdf|png|jpe?g)$/i.test(file.name||'')))return 'Bill chỉ nhận PDF, PNG hoặc JPEG.';
 return '';
}
export function hasPendingPayment(submissions,kind){return (submissions||[]).some(item=>item.kind===kind&&item.status==='PENDING');}
export function validateStudentPayment(values,option,file,{today=paymentToday(),submissions=[]}={}){
 const errors={},amount=paymentAmount(values.amount);
 if(!option||!Object.hasOwn(paymentKinds,values.kind))errors.kind='Chọn khoản phải nộp theo hợp đồng.';
 if(amount===null)errors.amount='Nhập số tiền nguyên lớn hơn 0 đồng.';
 else if(amount>Number(option?.remaining||0))errors.amount='Số tiền vượt khoản còn phải nộp đã được xác nhận.';
 if(!values.payerName?.trim()||values.payerName.trim().length>160)errors.payerName='Nhập tên người chuyển khoản, tối đa 160 ký tự.';
 if(!validPaymentDate(values.paidOn,today))errors.paidOn='Chọn ngày chuyển khoản hợp lệ, không ở tương lai.';
 if(values.transferMemo!==option?.transferMemo)errors.transferMemo='Nội dung chuyển khoản phải đúng mã do hệ thống cung cấp.';
 const billError=validatePaymentBill(file);if(billError)errors.receiptFileId=billError;
 if(hasPendingPayment(submissions,values.kind))errors.kind='Khoản này đã có bill chờ kế toán đối chiếu.';
 if(String(values.declaredBankReference||'').trim().length>120)errors.declaredBankReference='Mã giao dịch tối đa 120 ký tự.';
 if(String(values.note||'').trim().length>1000)errors.note='Ghi chú tối đa 1.000 ký tự.';
 return errors;
}
export function studentPaymentPayload(values,option,receiptFileId){
 return {kind:values.kind,amount:Number(values.amount),payerName:values.payerName.trim(),paidOn:values.paidOn,transferMemo:option.transferMemo,receiptFileId,
  declaredBankReference:String(values.declaredBankReference||'').trim(),note:String(values.note||'').trim()};
}
export function validatePaymentReview(values,submission,expectedMemo,today=paymentToday()){
 const errors={};
 if(!String(values.bankReference||'').trim()||String(values.bankReference).trim().length>120)errors.bankReference='Nhập mã giao dịch trên sao kê, tối đa 120 ký tự.';
 const amount=paymentAmount(values.statementAmount);
 if(amount===null||amount!==Number(submission.amount))errors.statementAmount='Số tiền trên sao kê phải khớp số tiền trên bill.';
 if(normalizedTransferMemo(values.statementMemo)!==normalizedTransferMemo(expectedMemo))errors.statementMemo='Nội dung trên sao kê chưa khớp nội dung chuyển khoản bắt buộc.';
 if(!validPaymentDate(values.statementDate,today))errors.statementDate='Chọn ngày trên sao kê hợp lệ, không ở tương lai.';
 if(!values.identityVerified||!values.amountVerified||!values.referenceVerified)errors.verified='Đánh dấu đầy đủ các mục đối chiếu trước khi xác nhận.';
 if(!String(values.changeNote||'').trim()||String(values.changeNote).trim().length>1000)errors.changeNote='Nhập ý kiến xác nhận, tối đa 1.000 ký tự.';
 return errors;
}
export function paymentReviewPayload(values,version){
 return {version,bankReference:values.bankReference.trim(),statementAmount:Number(values.statementAmount),statementMemo:values.statementMemo.trim(),statementDate:values.statementDate,
  identityVerified:Boolean(values.identityVerified),amountVerified:Boolean(values.amountVerified),referenceVerified:Boolean(values.referenceVerified),changeNote:values.changeNote.trim()};
}
