import {canScreen, screenPath, screenTitle} from './access';
import './equipment-request-navigation.css';

const requestScreens = ['ListEquipmentRequest', 'InitialEquipmentRequests'];

export default function EquipmentRequestNavigation({activeCode, user, functions, onNavigate}) {
  const available = requestScreens.filter(code => canScreen(code, user, functions));
  if (available.length < 2) return null;

  return <nav className="equipment-request-navigation" aria-label="Yêu cầu cấp thiết bị">
    {available.map(code => {
      const href = screenPath(code);
      const active = code === activeCode;
      return <a key={code} href={href} aria-current={active ? 'page' : undefined} onClick={event => {
        if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
        event.preventDefault();
        if (!active) onNavigate(href);
      }}>{screenTitle(code, user)}</a>;
    })}
  </nav>;
}
