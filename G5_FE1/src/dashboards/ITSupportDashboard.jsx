import React from 'react';
import { Box, Monitor, Users, Wrench } from 'lucide-react';
import { BarChart, DashboardIntro, DashboardPage, focusPanel, RecordPanel, StatsGrid, SummaryBanner, TaskPanel } from './DashboardUI';

export default function ITSupportDashboard({ data }) {
  const panels = data.panels || [];
  return <DashboardPage data={data} className="dash-it-support">
    <DashboardIntro eyebrow="IT SUPPORT / QUẢN LÝ THIẾT BỊ" title="Tổng quan thiết bị & hỗ trợ" description="Điều phối cấp phát, bàn giao, thu hồi và bảo trì thiết bị của trung tâm." action="Xem yêu cầu cấp phát" icon={Monitor} onAction={() => focusPanel(panels[0])}/>
    <SummaryBanner title={<>Đúng thiết bị.<br/>Đúng người, đúng thời điểm.</>} description="Ưu tiên yêu cầu và theo dõi tài sản trong suốt quá trình sử dụng." highlights={data.highlights}/>
    <StatsGrid stats={data.stats} icons={[Box, Users, Monitor, Wrench]}/>
    <div className="dash-main-grid"><div className="dash-stack"><RecordPanel panel={panels[0]}/>{panels.slice(1).map(panel => <RecordPanel key={panel.key} panel={panel}/>)}<BarChart chart={data.chart}/></div><aside className="dash-stack"><TaskPanel tasks={data.tasks} title="Ưu tiên xử lý" description="Điều phối theo yêu cầu và tình trạng thiết bị."/></aside></div>
  </DashboardPage>;
}
