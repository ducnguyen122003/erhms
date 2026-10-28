import React, { createContext, useContext, useEffect, useId, useRef, useState } from 'react';
import { ArrowUpRight, CalendarDays, Check, ChevronRight, CircleHelp, Clock3, FileText, Inbox, ShieldCheck, X } from 'lucide-react';
import './dashboard.css';

const DetailsContext = createContext(() => {});
const numberFormat = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 });
const currencyFormat = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 });

export function formatMetric(value, unit = '') {
  if (value === null || value === undefined || value === '') return '—';
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return String(value);
  if (['VND', 'VNĐ', 'đ', '₫'].includes(unit)) {
    if (Math.abs(parsed) >= 1_000_000_000) return `${numberFormat.format(parsed / 1_000_000_000)} tỷ ₫`;
    if (Math.abs(parsed) >= 1_000_000) return `${numberFormat.format(parsed / 1_000_000)} tr ₫`;
    return currencyFormat.format(parsed);
  }
  return `${numberFormat.format(parsed)}${unit === '%' ? '%' : ''}`;
}

export function panelId(key) { return `dash-panel-${key}`; }

export function focusPanel(panel) {
  if (!panel) return;
  const element = document.getElementById(panelId(panel.key));
  element?.scrollIntoView({ behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth', block: 'start' });
  element?.focus({ preventScroll: true });
}

function DetailDialog({ detail, onClose }) {
  const ref = useRef(null);
  const titleId = useId();
  useEffect(() => {
    const previouslyFocused = document.activeElement;
    const dialog = ref.current;
    dialog.showModal();
    return () => { dialog.close(); previouslyFocused?.focus(); };
  }, []);
  return <dialog ref={ref} className="dash-dialog" aria-labelledby={titleId} onCancel={onClose} onClick={event => { if (event.target === ref.current) onClose(); }}>
    <div className="dash-dialog-heading"><div><span className="dash-eyebrow">THÔNG TIN TỪ HỆ THỐNG</span><h2 id={titleId}>{detail.title}</h2></div><button type="button" className="dash-icon-button" aria-label="Đóng chi tiết" onClick={onClose}><X size={20}/></button></div>
    <div className="dash-dialog-body">
      {detail.row.id && <p className="dash-record-id">Mã bản ghi: {detail.row.id}</p>}
      <dl className="dash-detail-list">{(detail.row.cells || []).map((cell, index) => <div key={index}><dt>{detail.columns[index] || `Thông tin ${index + 1}`}</dt><dd>{cell || 'Chưa có thông tin'}</dd></div>)}</dl>
      {detail.row.status && <div className="dash-detail-status"><span>Trạng thái</span><StatusBadge value={detail.row.status} tone={detail.row.statusTone}/></div>}
      {detail.row.detail && <p className="dash-detail-note">{detail.row.detail}</p>}
    </div>
    <div className="dash-dialog-footer"><button type="button" className="dash-button" onClick={onClose}><Check size={15}/>Đã xem</button></div>
  </dialog>;
}

export function DashboardPage({ children, className = '' }) {
  const [detail, setDetail] = useState(null);
  return <DetailsContext.Provider value={setDetail}><div className={`dashboard-page ${className}`}>
    {children}
    {detail && <DetailDialog detail={detail} onClose={() => setDetail(null)}/>}
  </div></DetailsContext.Provider>;
}

export function DashboardIntro({ eyebrow, title, description, action, icon: Icon = CalendarDays, onAction }) {
  return <header className="dash-intro"><div><span className="dash-eyebrow">{eyebrow}</span><h1>{title}</h1><p>{description}</p></div>{action && <button className="dash-button" type="button" onClick={onAction}><Icon size={15}/>{action}</button>}</header>;
}

export function SummaryBanner({ title, description, highlights = [] }) {
  return <section className="dash-banner" aria-label="Tổng quan công việc"><div className="dash-banner-message"><span>TỔNG QUAN CÔNG VIỆC</span><h2>{title}</h2>{description && <p>{description}</p>}</div>{highlights.length > 0 && <div className="dash-highlights">{highlights.map((item, index) => <div key={item.key || index}><span>{item.label}</span><strong>{formatMetric(item.value, item.unit)}{item.unit && !['%', 'VND', 'VNĐ', 'đ', '₫'].includes(item.unit) && <small> {item.unit}</small>}</strong><p>{item.description}</p></div>)}</div>}</section>;
}

export function StatsGrid({ stats = [], icons = [] }) {
  return <section className="dash-stats" aria-label="Các chỉ số tổng quan">{stats.map((item, index) => {
    const Icon = icons[index % (icons.length || 1)] || FileText;
    return <article className="dash-stat" key={item.key || index}><div><p>{item.label}</p><strong title={['VND', 'VNĐ', 'đ', '₫'].includes(item.unit) && item.value !== null ? currencyFormat.format(item.value) : undefined}>{formatMetric(item.value, item.unit)}{item.unit && !['%', 'VND', 'VNĐ', 'đ', '₫'].includes(item.unit) && <small> {item.unit}</small>}</strong></div><span className={`dash-stat-icon dash-tone-${item.tone || 'green'}`}><Icon size={17}/></span><p className="dash-stat-description">{item.description}</p></article>;
  })}</section>;
}

export function StatusBadge({ value, tone = 'neutral' }) {
  return <span className={`dash-badge dash-tone-${tone}`}><i aria-hidden="true"/>{value}</span>;
}

function EmptyPanel({ message = 'Chưa có bản ghi phù hợp.' }) {
  return <div className="dash-empty"><Inbox size={23}/><p>{message}</p><span>Dữ liệu sẽ hiển thị khi được cập nhật vào hệ thống.</span></div>;
}

export function RecordPanel({ panel, actionLabel = 'Xem chi tiết' }) {
  const showDetail = useContext(DetailsContext);
  if (!panel) return null;
  const rows = panel.rows || [];
  const columns = panel.columns || [];
  const hasStatus = rows.some(row => row.status);
  return <section className="dash-panel" id={panelId(panel.key)} tabIndex={-1} aria-label={panel.title}>
    <div className="dash-panel-heading"><div><h2>{panel.title}</h2>{panel.description && <p>{panel.description}</p>}</div><span className="dash-panel-count">{rows.length} bản ghi</span></div>
    {rows.length ? <><div className="dash-table-scroll"><table className="dash-table"><thead><tr>{columns.map((column, index) => <th key={index} scope="col">{column}</th>)}{hasStatus && <th scope="col">Trạng thái</th>}<th scope="col"><span className="dash-sr-only">Thao tác</span></th></tr></thead><tbody>{rows.map((row, index) => <tr key={`${row.id}-${index}`}>{(row.cells || []).map((cell, cellIndex) => <td key={cellIndex}>{cellIndex === 0 ? <><button className="dash-cell-link" onClick={() => showDetail({ title: panel.title, row, columns })}>{cell || 'Chưa có thông tin'}</button>{row.detail && <small>{row.detail}</small>}</> : cell || '—'}</td>)}{hasStatus && <td>{row.status ? <StatusBadge value={row.status} tone={row.statusTone}/> : '—'}</td>}<td className="dash-row-action"><button type="button" className="dash-record-action" onClick={() => showDetail({ title: panel.title, row, columns })} aria-label={`${actionLabel}: ${row.cells?.[0] || row.id}`}><span>Chi tiết</span><ArrowUpRight size={14}/></button></td></tr>)}</tbody></table></div><div className="dash-panel-bottom"><span>{rows.length} bản ghi gần nhất</span><span><ShieldCheck size={11}/>Dữ liệu trong phạm vi quyền truy cập</span></div></> : <EmptyPanel/>}
  </section>;
}

export function RecordCards({ panel }) {
  const showDetail = useContext(DetailsContext);
  if (!panel) return null;
  return <section className="dash-panel" id={panelId(panel.key)} tabIndex={-1} aria-label={panel.title}><div className="dash-panel-heading"><div><h2>{panel.title}</h2><p>{panel.description}</p></div></div>{panel.rows?.length ? <div className="dash-record-cards">{panel.rows.map((row, index) => <button type="button" className="dash-record-card" key={`${row.id}-${index}`} onClick={() => showDetail({ title: panel.title, row, columns: panel.columns || [] })}><span className="dash-record-card-code">{row.id}</span><strong>{row.cells?.[0] || 'Xem bản ghi'}</strong>{row.cells?.slice(1).map((cell, cellIndex) => <span key={cellIndex}>{panel.columns?.[cellIndex + 1]}: {cell || '—'}</span>)}{row.status && <StatusBadge value={row.status} tone={row.statusTone}/>}<span className="dash-card-view">Xem chi tiết<ArrowUpRight size={13}/></span></button>)}</div> : <EmptyPanel/>}</section>;
}

export function TaskPanel({ tasks = [], title = 'Việc cần hoàn tất', description = 'Những nội dung cần chú ý trong phạm vi công việc.' }) {
  const showDetail = useContext(DetailsContext);
  const icons = [FileText, CircleHelp, Clock3, ShieldCheck];
  return <section className="dash-panel dash-tasks" aria-label={title}><div className="dash-panel-heading"><div><h2>{title}</h2><p>{description}</p></div></div>{tasks.length ? <div className="dash-task-list">{tasks.map((task, index) => {
    const Icon = icons[index % icons.length];
    return <button type="button" key={task.key || index} className="dash-task" onClick={() => showDetail({ title: task.title, columns: ['Nội dung', 'Số lượng'], row: { cells: [task.description, formatMetric(task.count)], detail: 'Số liệu được tổng hợp từ cơ sở dữ liệu theo quyền của tài khoản hiện tại.' } })}><span className={`dash-task-icon dash-tone-${task.tone || 'green'}`}><Icon size={16}/></span><span className="dash-task-copy"><strong>{task.title}</strong><small>{task.description}</small></span><span className={`dash-task-count dash-text-${task.tone || 'green'}`}>{formatMetric(task.count)}</span><ChevronRight size={14}/></button>;
  })}</div> : <EmptyPanel message="Chưa có công việc cần chú ý."/>}</section>;
}

export function BarChart({ chart }) {
  if (!chart) return null;
  const points = chart.points || [];
  const maximum = Math.max(1, ...points.map(point => Number(point.value) || 0));
  return <section className="dash-panel dash-chart" aria-label={chart.title}><div className="dash-panel-heading"><div><h2>{chart.title}</h2><p>{chart.description}</p></div><span className="dash-chart-tag"><span/>Từ cơ sở dữ liệu</span></div>{points.length ? <div className="dash-bar-chart" role="img" aria-label={`${chart.title}: ${points.map(point => `${point.label}: ${formatMetric(point.value)}`).join('; ')}`}><div className="dash-chart-grid" aria-hidden="true"/><div className="dash-bars">{points.map((point, index) => <div className="dash-bar-column" key={`${point.label}-${index}`}><div className="dash-bar-track"><div className={`dash-bar ${index === points.length - 1 ? 'dash-bar-current' : ''}`} style={{ height: `${Math.max(0, (Number(point.value) || 0) / maximum * 100)}%` }}><span>{formatMetric(point.value)}</span></div></div><span className="dash-bar-label">{point.label}</span></div>)}</div></div> : <EmptyPanel message="Chưa có dữ liệu cho biểu đồ."/>}</section>;
}

export function ProgressPanel({ progress }) {
  if (!progress) return null;
  const completed = Number(progress.completed) || 0;
  const total = Number(progress.total) || 0;
  const absent = Number(progress.absent) || 0;
  const percentage = total > 0 ? Math.max(0, Math.min(100, Math.round(completed / total * 100))) : null;
  return <section className="dash-panel dash-progress"><div className="dash-panel-heading"><div><h2>Tiến độ khóa học</h2><p>Theo lịch học và điểm danh của bạn.</p></div></div><div className="dash-progress-body"><div className="dash-donut" style={{ '--dash-progress': `${percentage || 0}%` }} role="img" aria-label={percentage === null ? 'Chưa có dữ liệu tiến độ' : `Tiến độ: ${percentage}%, ${completed} trên ${total} buổi đã diễn ra`}><div><strong>{percentage === null ? '—' : `${percentage}%`}</strong><span>{completed} / {total} buổi</span></div></div><dl className="dash-progress-legend"><div><dt><i/>Đã diễn ra</dt><dd>{formatMetric(completed)} buổi</dd></div><div><dt><i/>Vắng học</dt><dd>{formatMetric(absent)} buổi</dd></div><div><dt><i/>Chưa diễn ra</dt><dd>{formatMetric(Math.max(0, total - completed))} buổi</dd></div></dl></div>{progress.endDate && <p className="dash-progress-end"><CalendarDays size={14}/>Dự kiến hoàn thành: {new Date(`${progress.endDate}T00:00:00`).toLocaleDateString('vi-VN')}</p>}</section>;
}

export function firstName(user) {
  return (user?.fullName || user?.name || '').trim().split(/\s+/).pop() || 'bạn';
}
