import React from 'react';
import { CalendarDays, ClipboardList, FileText, Monitor, Wallet } from 'lucide-react';
import { BarChart, DashboardIntro, DashboardPage, firstName, focusPanel, ProgressPanel, RecordPanel, StatsGrid, SummaryBanner, TaskPanel } from './DashboardUI';

export default function StudentDashboard({ data, user }) {
  const panels = data.panels || [];
  return <DashboardPage data={data} className="dash-student">
    <DashboardIntro eyebrow="HỌC VIÊN / HÀNH TRÌNH HỌC TẬP" title={`Chào ${firstName(user)}, tiếp tục nhé!`} description="Theo dõi lịch học, chuyên cần, kết quả và thiết bị được cấp của bạn." action="Xem lịch học" icon={CalendarDays} onAction={() => focusPanel(panels[0])}/>
    <SummaryBanner title={<>Mỗi buổi học,<br/>thêm một bước vững tay nghề.</>} description="Cập nhật hành trình học tập từ dữ liệu của chính bạn." highlights={data.highlights}/>
    <StatsGrid stats={data.stats} icons={[ClipboardList, FileText, Wallet, Monitor]}/>
    <div className="dash-main-grid"><div className="dash-stack"><RecordPanel panel={panels[0]}/>{panels.slice(1, 2).map(panel => <RecordPanel key={panel.key} panel={panel}/>)}<BarChart chart={data.chart}/></div><aside className="dash-stack"><ProgressPanel progress={data.progress}/>{panels.slice(2).map(panel => <RecordPanel key={panel.key} panel={panel}/>)}<TaskPanel tasks={data.tasks} title="Thông tin cần chú ý" description="Các cập nhật trong quá trình học tập của bạn."/></aside></div>
  </DashboardPage>;
}
