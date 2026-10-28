import React from 'react';
import { BookOpen, ClipboardList, RefreshCw, Users } from 'lucide-react';
import { BarChart, DashboardIntro, DashboardPage, focusPanel, RecordPanel, StatsGrid, SummaryBanner, TaskPanel } from './DashboardUI';

export default function ManagerDashboard({ data }) {
  const panels = data.panels || [];
  return <DashboardPage data={data} className="dash-manager">
    <DashboardIntro eyebrow="QUẢN LÝ HỌC VIÊN / ĐIỀU PHỐI ĐÀO TẠO" title="Theo sát từng lớp, từng học viên" description="Quản lý hồ sơ, kiểm tra xếp lớp và theo dõi đến khi hoàn tất khóa học." action="Xem hồ sơ học viên" icon={Users} onAction={() => focusPanel(panels[0])}/>
    <SummaryBanner title={<>Đào tạo thông suốt.<br/>Mọi hồ sơ có bước tiếp theo.</>} description="Hệ thống cập nhật tình hình học viên và lớp học trong phạm vi quản lý." highlights={data.highlights}/>
    <StatsGrid stats={data.stats} icons={[Users, BookOpen, RefreshCw, ClipboardList]}/>
    <div className="dash-main-grid"><div className="dash-stack"><RecordPanel panel={panels[0]}/>{panels.slice(1).map(panel => <RecordPanel key={panel.key} panel={panel}/>)}<BarChart chart={data.chart}/></div><aside className="dash-stack"><TaskPanel tasks={data.tasks} title="Việc điều phối cần chú ý" description="Theo dõi và xử lý các nội dung trong phạm vi quản lý."/></aside></div>
  </DashboardPage>;
}
