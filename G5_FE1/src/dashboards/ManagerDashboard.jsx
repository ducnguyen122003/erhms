import React from 'react';
import {canScreen} from '../screens/access';
import { BookOpen, ClipboardList, RefreshCw, Users, FileText } from 'lucide-react';
import { BarChart, DashboardIntro, DashboardPage, focusPanel, RecordPanel, StatsGrid, SummaryBanner, TaskPanel } from './DashboardUI';

export default function ManagerDashboard({ data, onNavigate, user, functions=[] }) {
  const panels = data.panels || [],canCreate=canScreen('AddTrainingContract',user,functions);
  return <DashboardPage data={data} className="dash-manager">
    <DashboardIntro eyebrow="QUẢN LÝ HỌC VIÊN / ĐIỀU PHỐI ĐÀO TẠO" title="Theo sát từng lớp, từng học viên" description="Lập hợp đồng từ hồ sơ đã liên hệ, quản lý lớp và theo dõi đến khi hoàn tất khóa học." action={canCreate?'Tạo hợp đồng đào tạo':'Xem hồ sơ học viên'} icon={canCreate?FileText:Users} onAction={() => onNavigate ? onNavigate(canCreate?'/screens/AddTrainingContract':'/screens/ListStudent') : focusPanel(panels[0])}/>
    <SummaryBanner title={<>Đào tạo thông suốt.<br/>Mọi hồ sơ có bước tiếp theo.</>} description="Hệ thống cập nhật tình hình học viên và lớp học trong phạm vi quản lý." highlights={data.highlights}/>
    <StatsGrid stats={data.stats} icons={[Users, BookOpen, RefreshCw, ClipboardList]}/>
    <div className="dash-main-grid"><div className="dash-stack"><RecordPanel panel={panels[0]}/>{panels.slice(1).map(panel => <RecordPanel key={panel.key} panel={panel}/>)}<BarChart chart={data.chart}/></div><aside className="dash-stack"><TaskPanel tasks={data.tasks} title="Việc điều phối cần chú ý" description="Theo dõi và xử lý các nội dung trong phạm vi quản lý."/></aside></div>
  </DashboardPage>;
}
