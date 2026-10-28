import React from 'react';
import { Activity, LockKeyhole, ShieldCheck, Users } from 'lucide-react';
import { BarChart, DashboardIntro, DashboardPage, RecordPanel, StatsGrid, TaskPanel } from './DashboardUI';

export default function AdminDashboard({ data, onNavigate }) {
  const panels = data.panels || [];
  return <DashboardPage data={data} className="dash-admin">
    <DashboardIntro eyebrow="QUẢN TRỊ / TỔNG QUAN HỆ THỐNG" title="Tổng quan hệ thống" description="Theo dõi tài khoản, quyền truy cập và tình trạng kết nối tại một nơi." action="Quản lý tài khoản" icon={Users} onAction={() => onNavigate('/admin/users')}/>
    <StatsGrid stats={data.stats} icons={[Users, Activity, ShieldCheck, LockKeyhole]}/>
    <div className="dash-main-grid"><div className="dash-stack"><RecordPanel panel={panels[0]}/><BarChart chart={data.chart}/>{panels.slice(1).map(panel => <RecordPanel key={panel.key} panel={panel}/>)}</div><aside className="dash-stack"><TaskPanel tasks={data.tasks} title="Cần quản trị viên chú ý" description="Các nội dung thuộc phạm vi quản trị."/></aside></div>
  </DashboardPage>;
}
