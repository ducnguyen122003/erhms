import {Check, Circle} from 'lucide-react';
import {display} from './definitions';

const number = new Intl.NumberFormat('vi-VN', {maximumFractionDigits: 4});

function Condition({label, description, complete}) {
 const StatusIcon = complete ? Check : Circle;

 return <p className={complete ? 'is-complete' : undefined}>
  <StatusIcon size={14} aria-hidden="true" />
  {label}
  <small>{description}</small>
 </p>;
}

function AcademicRequirements({details}) {
 const attendance = details.totalSessions > 0
  ? `${details.attendedSessions}/${details.totalSessions} buổi (${number.format(details.attendancePercent)}%)`
  : 'Chưa có buổi học';
 const attendanceStatus = details.totalSessions > 0 && !details.attendanceComplete
  ? ' · Chờ chốt tất cả các buổi học'
  : '';

 return <>
  <Condition
   label="Tham dự ít nhất 80% số buổi"
   description={attendance + attendanceStatus}
   complete={details.attendanceComplete && details.attendancePass}
  />
  <Condition
   label="Điểm cuối kỳ trên 4"
   description={details.finalScore == null ? 'Chưa công bố điểm cuối kỳ' : `${number.format(details.finalScore)}/10`}
   complete={details.finalPass}
  />
  <Condition
   label="Tổng điểm trên 5"
   description={details.totalScore == null ? 'Chờ công bố đủ các đầu điểm' : `${number.format(details.totalScore)}/10`}
   complete={details.totalPass}
  />
 </>;
}

export default function ClosureConditions({record, conditions, showAcademicDetails, canWrite, can, go, onAcademic, onAcademicFailure, onClose, code, busy}) {
 const {academic, academicDetails, academicEligible, academicFailed, academicFailureEligible, assets, finance, ready, withdrawal} = conditions;
 let academicStatus = 'Chưa đủ điều kiện';
 if (withdrawal) academicStatus = 'Kết thúc do nghỉ học';
 else if (academic) academicStatus = record.academicCompletedAt
  ? `Đã xác nhận · ${display(record.academicCompletedAt, 'academicCompletedAt')}`
  : 'Đã xác nhận';
 else if (academicFailed) academicStatus = record.academicFailedAt
  ? `Đã xác nhận chưa đạt · ${display(record.academicFailedAt, 'academicFailedAt')}`
  : 'Đã xác nhận chưa đạt';
 else if (academicEligible) academicStatus = 'Đủ điều kiện, chờ xác nhận';
 else if (academicFailureEligible) academicStatus = 'Chưa đạt, chờ xác nhận kết quả';

 const isOverview = code === 'TrainingClosureDetail';
 const canCloseHere = code === 'TrainingClosureDetail_Ready' || code === 'TrainingClosureDetail_Withdrawal';

 return <section className="screen-conditions">
  <h3>Điều kiện đóng hồ sơ</h3>
  <Condition label="Hoàn thành khóa học" description={academicStatus} complete={academic && !withdrawal} />
  {showAcademicDetails && !withdrawal && academicDetails && <AcademicRequirements details={academicDetails} />}
  {canWrite && !record.closedAt && isOverview && !academic && !academicFailed && !withdrawal && <button
   type="button"
   className="admin-secondary"
   disabled={busy || (!academicEligible && !academicFailureEligible)}
   onClick={academicFailureEligible ? onAcademicFailure : onAcademic}
  >{academicFailureEligible ? 'Xác nhận chưa đạt yêu cầu' : 'Xác nhận hoàn thành khóa học'}</button>}
  <Condition label="Thu hồi thiết bị" description={assets ? 'Đã hoàn tất' : 'Chờ thu hồi'} complete={assets} />
  <Condition label="Quyết toán tài chính" description={finance ? 'Đã hoàn tất' : 'Chờ quyết toán'} complete={finance} />
  {canWrite && !record.closedAt && <>
   {ready && isOverview && can('TrainingClosureDetail_Ready') && <button type="button" disabled={busy} onClick={() => go('TrainingClosureDetail_Ready', record.id)}>Hồ sơ đủ điều kiện</button>}
   {canCloseHere && <button type="button" disabled={busy || !ready} onClick={onClose}>Đóng hồ sơ</button>}
   {can('RecordWithdrawal') && !withdrawal && !academic && !academicFailed && <button type="button" className="admin-text-button" disabled={busy} onClick={() => go('RecordWithdrawal', record.id)}>Ghi nhận nghỉ giữa chừng</button>}
  </>}
 </section>;
}
