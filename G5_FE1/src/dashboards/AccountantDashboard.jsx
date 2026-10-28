import React from 'react';
import { CircleDollarSign, Clock3, RefreshCw, ShieldCheck, Wrench } from 'lucide-react';
import { BarChart, DashboardIntro, DashboardPage, focusPanel, RecordPanel, StatsGrid, SummaryBanner, TaskPanel } from './DashboardUI';

export default function AccountantDashboard({ data }) {
  const panels = data.panels || [];
  return <DashboardPage data={data} className="dash-accountant">
    <DashboardIntro eyebrow="KẾ TOÁN / HỌC PHÍ & THANH TOÁN" title="Tổng quan tài chính đào tạo" description="Đối soát giao dịch theo hợp đồng, theo dõi học phí và tiền cọc thiết bị." action="Xem giao dịch" icon={RefreshCw} onAction={() => focusPanel(panels[0])}/>
    <SummaryBanner title={<>Dòng tiền rõ ràng.<br/>Đúng từng hợp đồng đào tạo.</>} description="Theo dõi học phí, thanh toán và các khoản cần xử lý." highlights={data.highlights}/>
    <StatsGrid stats={data.stats} icons={[CircleDollarSign, ShieldCheck, Clock3, Wrench]}/>
    <div className="dash-main-grid"><div className="dash-stack"><RecordPanel panel={panels[0]}/><BarChart chart={data.chart}/>{panels.slice(1).map(panel => <RecordPanel key={panel.key} panel={panel}/>)}</div><aside className="dash-stack"><TaskPanel tasks={data.tasks} title="Hồ sơ cần xử lý" description="Thanh toán, tiền cọc và các khoản cần kiểm tra."/></aside></div>
  </DashboardPage>;
}
