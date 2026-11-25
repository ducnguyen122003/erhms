import {display} from './definitions';

const moneyFormat=new Intl.NumberFormat('vi-VN',{style:'currency',currency:'VND',maximumFractionDigits:2});
const displayMoney=value=>value==null?'Chưa cập nhật':moneyFormat.format(value);

export const refundRequestFields=[
 {key:'refundPercent',label:'Tỷ lệ hoàn cọc (%)',type:'number',required:true,min:0,max:100,step:1},
 {key:'reason',label:'Lý do xác định tỷ lệ hoàn cọc',type:'textarea',required:true,maxLength:1000},
 {key:'recipientName',label:'Tên chủ tài khoản nhận tiền',type:'text',required:true,maxLength:160},
 {key:'bankName',label:'Ngân hàng nhận tiền',type:'text',required:true,maxLength:120},
 {key:'accountNumber',label:'Số tài khoản nhận tiền',type:'text',required:true,maxLength:34,pattern:'[0-9]{4,34}',inputMode:'numeric',placeholder:'Từ 4 đến 34 chữ số'},
];

export function refundRequestValues(contractVersion,studentName,review){
 return {
  contractVersion,
  refundPercent:review?.refundPercent??100,
  reason:review?.reason??'',
  recipientName:review?.recipientName??studentName??'',
  bankName:review?.bankName??'',
  accountNumber:review?.accountNumber??'',
 };
}

export function refundRequestPayload({contractVersion,refundPercent,reason,recipientName,bankName,accountNumber}){
 return {contractVersion,refundPercent,reason,recipientName,bankName,accountNumber};
}

export function refundTransferFields(review){
 const hasTransfer=Number(review.refundAmount)>0;
 return [
  {key:'bankReference',label:hasTransfer?'Mã giao dịch chuyển khoản':'Mã chứng từ quyết toán',type:'text',required:true,maxLength:120},
  {key:'evidence',label:hasTransfer?'Chứng từ chuyển khoản':'Minh chứng quyết toán',type:'evidence',required:true},
  {key:'recipientVerified',label:hasTransfer?'Đã đối chiếu người nhận, số tài khoản và số tiền chuyển':'Đã đối chiếu phiếu IT và xác nhận số tiền hoàn bằng 0',type:'checkbox',required:true},
 ];
}

export function refundTransferPayload({version,bankReference,evidence,recipientVerified}){
 return {version,bankReference,evidence,recipientVerified};
}

export function RefundReview({context,review,showStatus=false}){
 return <section className="screen-related">
  <h3>Phiếu đề nghị hoàn cọc của IT</h3>
  {showStatus&&<p className="admin-muted">Cọc đã xác nhận {displayMoney(context.depositAmount)}</p>}
  {review&&<dl className="screen-facts">
   <div><dt>Tỷ lệ hoàn cọc</dt><dd>{review.refundPercent}%</dd></div>
   <div><dt>Số tiền đề nghị hoàn</dt><dd>{displayMoney(review.refundAmount)}</dd></div>
   <div className="screen-field-wide"><dt>Lý do</dt><dd>{review.reason}</dd></div>
   <div><dt>Tên chủ tài khoản</dt><dd>{review.recipientName}</dd></div>
   <div><dt>Ngân hàng</dt><dd>{review.bankName}</dd></div>
   <div><dt>Số tài khoản</dt><dd>{review.accountNumber}</dd></div>
   <div><dt>Cập nhật lần cuối</dt><dd>{display(review.updatedAt||review.createdAt,'createdAt')}</dd></div>
  </dl>}
  {showStatus&&<p className="admin-muted">{context.settled?'Kế toán đã quyết toán tiền cọc.':context.reason||(review?'Đã gửi phiếu cho kế toán.':'Chưa có phiếu đề nghị hoàn cọc.')}</p>}
 </section>;
}

export function DepositSettlementOverview({record,context,review,incidents,settlement,children}){
 const paid=Number(context.depositAmount??record.depositPaid??0);
 const legacyDeduction=incidents.filter(incident=>!incident.settledAt).reduce((total,incident)=>total+Number(incident.approvedAmount||0),0);
 const refund=settlement?.refundAmount??(context.settled?record.refundPaid:null)??review?.refundAmount??(context.needsItReview?null:Math.max(0,paid-legacyDeduction));
 const deduction=settlement?.deductionAmount??(refund===null?null:Number(review?.depositAmount??paid)-Number(refund));
 return <section className="admin-card screen-settlement">
  <h2>{record.studentName||'Quyết toán tiền cọc'}</h2>
  <p className="admin-muted">Hợp đồng {record.code}</p>
  <div>
   <span>Cọc đã xác nhận<strong>{displayMoney(paid)}</strong></span>
   <span>{context.settled?'Đã khấu trừ':'Dự kiến khấu trừ'}<strong>{displayMoney(deduction)}</strong></span>
   <span>{context.settled?'Đã hoàn':'Dự kiến hoàn'}<strong>{displayMoney(refund)}</strong></span>
  </div>
  {(context.needsItReview||review)&&<RefundReview context={context} review={review}/>}
  {!context.settled&&!context.canSettle&&<p className="admin-message">{context.reason||'Hồ sơ chưa đủ điều kiện quyết toán.'}</p>}
  {context.canSettle&&Number(review?.refundAmount)>0&&<p className="admin-muted">Chuyển khoản theo thông tin trên phiếu rồi ghi nhận giao dịch sau khi chuyển thành công.</p>}
  {children}
 </section>;
}
