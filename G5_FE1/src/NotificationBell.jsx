import {useEffect, useId, useRef, useState} from 'react';
import {ArrowRight, Bell} from 'lucide-react';
import {api} from './api';
import {screenPath} from './screens/access';
import './notification-bell.css';

const listEndpoint = '/api/workspace/screens/ListNotification';
const dateFormat = new Intl.DateTimeFormat('vi-VN', {
  dateStyle: 'short', timeStyle: 'short', timeZone: 'Asia/Ho_Chi_Minh',
});

export default function NotificationBell({canList, canView, canRetake, route, revision, onNavigate, onLogout}) {
  const [open, setOpen] = useState(false);
  const [unreadCount, setUnreadCount] = useState(null);
  const [preview, setPreview] = useState({items: null, error: false});
  const [refresh, setRefresh] = useState(0);
  const container = useRef(null);
  const trigger = useRef(null);
  const panelId = useId();

  useEffect(() => { setOpen(false); }, [route, canList]);

  useEffect(() => {
    if (!canList) return;
    const controller = new AbortController();
    api(`${listEndpoint}?status=UNREAD`, {signal: controller.signal})
      .then(data => {
        if (!controller.signal.aborted) setUnreadCount(data.total);
      })
      .catch(error => {
        if (!controller.signal.aborted && error.status === 401) onLogout();
      });
    return () => controller.abort();
  }, [canList, revision, refresh, onLogout]);

  useEffect(() => {
    if (!canList || !open) return;
    const controller = new AbortController();
    api(listEndpoint, {signal: controller.signal})
      .then(data => {
        if (!controller.signal.aborted) setPreview({items: data.items.slice(0, 5), error: false});
      })
      .catch(error => {
        if (controller.signal.aborted) return;
        if (error.status === 401) onLogout();
        else setPreview({items: null, error: true});
      });
    return () => controller.abort();
  }, [canList, open, revision, refresh, onLogout]);

  useEffect(() => {
    if (!open) return;
    function closeOnOutsidePress(event) {
      if (!container.current?.contains(event.target)) setOpen(false);
    }
    function closeOnEscape(event) {
      if (event.key !== 'Escape') return;
      setOpen(false);
      trigger.current?.focus();
    }
    document.addEventListener('pointerdown', closeOnOutsidePress);
    document.addEventListener('keydown', closeOnEscape);
    return () => {
      document.removeEventListener('pointerdown', closeOnOutsidePress);
      document.removeEventListener('keydown', closeOnEscape);
    };
  }, [open]);

  function reload() {
    setPreview({items: null, error: false});
    setRefresh(value => value + 1);
  }

  function toggle() {
    if (!open) reload();
    setOpen(!open);
  }

  function navigate(code, id) {
    setOpen(false);
    onNavigate(screenPath(code, id));
  }

  function openRetake(item) {
    const path=screenPath('StudentRetake',item.targetId);
    navigate('StudentRetake',item.targetId);
    if (item.readAt || window.location.pathname !== path) return;
    api(`${listEndpoint}/${item.id}/actions/read`, {method: 'POST', body: '{}'})
      .then(() => setRefresh(value => value + 1))
      .catch(error => { if (error.status === 401) onLogout(); });
  }

  if (!canList) return null;

  return <div className="workspace-notification" ref={container}>
    <button type="button" className="icon-btn workspace-notification-trigger" ref={trigger}
      aria-label={unreadCount > 0 ? `Thông báo, ${unreadCount} chưa đọc` : 'Thông báo'}
      aria-expanded={open} aria-controls={open ? panelId : undefined} onClick={toggle}>
      <Bell size={18} aria-hidden="true"/>
      {unreadCount > 0 && <span className="workspace-notification-count" aria-hidden="true">{unreadCount > 99 ? '99+' : unreadCount}</span>}
    </button>
    {open && <section id={panelId} className="workspace-notification-panel" aria-label="Thông báo">
      <div className="workspace-notification-heading"><h2>Thông báo</h2></div>
      {preview.error ? <div className="workspace-notification-message">
        <p role="alert">Chưa tải được thông báo.</p>
        <button type="button" className="workspace-notification-link" onClick={reload}>Thử lại</button>
      </div> : preview.items === null ? <p className="workspace-notification-message" role="status">Đang tải thông báo…</p>
        : preview.items.length === 0 ? <p className="workspace-notification-message">Bạn chưa có thông báo.</p>
        : <ul className="workspace-notification-list">
          {preview.items.map(item => {
            const unread = !item.readAt;
            const time = item.scheduledAt || item.createdAt;
            const retake = canRetake && item.targetScreen === 'StudentRetake' && item.targetId;
            const details = <>
              <strong>{item.title}</strong>
              {item.content && <span>{item.content}</span>}
              {time && <time dateTime={time}>{dateFormat.format(new Date(time))}</time>}
              {unread && <span className="sr-only">Chưa đọc</span>}
            </>;
            return <li key={item.id} className={unread ? 'is-unread' : undefined}>
              {retake || canView ? <button type="button" className="workspace-notification-item" onClick={() => retake ? openRetake(item) : navigate('ViewNotification', item.id)}>{details}</button>
                : <div className="workspace-notification-item">{details}</div>}
            </li>;
          })}
        </ul>}
      <div className="workspace-notification-footer">
        <button type="button" className="workspace-notification-link" onClick={() => navigate('ListNotification')}>
          Xem tất cả thông báo <ArrowRight size={15} aria-hidden="true"/>
        </button>
      </div>
    </section>}
  </div>;
}
