import React from 'react';
import { ClipboardList, FileText, Phone, Users } from 'lucide-react';
import { BarChart, DashboardIntro, DashboardPage, focusPanel, RecordPanel, StatsGrid, SummaryBanner, TaskPanel } from './DashboardUI';

export default function HRDashboard({ data, onNavigate }) {
  const panels = data.panels || [];
  return <DashboardPage data={data} className="dash-hr">
    <DashboardIntro eyebrow="TUYỂN DỤNG / TIẾP NHẬN & TƯ VẤN ĐÀO TẠO" title="Kết nối nhu cầu, mở hướng học" description="Tiếp nhận đăng ký từ website, liên hệ và ghi nhận kết quả tư vấn khóa học." action="Xem đăng ký tư vấn" icon={Phone} onAction={() => onNavigate ? onNavigate('/screens/ListAdmissionApplication') : focusPanel(panels[0])}/>
    <SummaryBanner title={<>Từ đăng ký quan tâm<br/>đến hồ sơ đã liên hệ.</>} description="Theo dõi liên hệ và lịch hẹn, chuyển hồ sơ đã liên hệ cho Manager lập hợp đồng." highlights={data.highlights}/>
    <StatsGrid stats={data.stats} icons={[Users, Phone, FileText, ClipboardList]}/>
    <div className="dash-main-grid"><div className="dash-stack"><RecordPanel panel={panels[0]}/><BarChart chart={data.chart}/>{panels.slice(1).map(panel => <RecordPanel key={panel.key} panel={panel}/>)}</div><aside className="dash-stack"><TaskPanel tasks={data.tasks} title="Công việc tuyển sinh hôm nay" description="Theo dõi hồ sơ theo từng bước tiếp nhận."/></aside></div>
  </DashboardPage>;
}
