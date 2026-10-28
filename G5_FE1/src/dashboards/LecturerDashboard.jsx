import React from 'react';
import { BookOpen, CalendarDays, ListChecks, MessageSquare, Users } from 'lucide-react';
import { BarChart, DashboardIntro, DashboardPage, firstName, focusPanel, RecordCards, RecordPanel, StatsGrid, SummaryBanner, TaskPanel } from './DashboardUI';

export default function LecturerDashboard({ data, user }) {
  const panels = data.panels || [];
  return <DashboardPage data={data} className="dash-lecturer">
    <DashboardIntro eyebrow="GIẢNG VIÊN / KHÔNG GIAN GIẢNG DẠY" title={`Chào ${firstName(user)}, sẵn sàng cho buổi học mới!`} description="Lịch dạy, điểm danh và tiến độ của các lớp được phân công." action="Xem lịch giảng dạy" icon={CalendarDays} onAction={() => focusPanel(panels[0])}/>
    <SummaryBanner title={<>Đồng hành mỗi buổi học.<br/>Vững vàng mỗi bước nghề.</>} description="Theo sát lớp học và những công việc cần hoàn tất." highlights={data.highlights}/>
    <StatsGrid stats={data.stats} icons={[BookOpen, Users, CalendarDays, MessageSquare]}/>
    <div className="dash-main-grid"><div className="dash-stack"><RecordPanel panel={panels[0]}/>{panels.slice(1).map(panel => <RecordCards key={panel.key} panel={panel}/>)}<BarChart chart={data.chart}/></div><aside className="dash-stack"><TaskPanel tasks={data.tasks} title="Việc cần hoàn tất" description="Chi tiết những công việc của lớp được giao."/></aside></div>
  </DashboardPage>;
}
