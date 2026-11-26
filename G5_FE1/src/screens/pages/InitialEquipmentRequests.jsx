import {AdminPage} from '../../admin/AdminUI';
import {dashboardRoles} from '../../roles';
import EquipmentRequestNavigation from '../EquipmentRequestNavigation';
import InitialEquipmentRequest from '../InitialEquipmentRequest';
import {screens} from '../access';

const code='InitialEquipmentRequests';

export default function InitialEquipmentRequests({user,functions,onNavigate,onLogout,onChanged}){
 const {title,description}=screens[code];
 return <AdminPage title={title} description={description} eyebrow={dashboardRoles[user.role]?.category} code={code}>
  <EquipmentRequestNavigation activeCode={code} user={user} functions={functions} onNavigate={onNavigate}/>
  <InitialEquipmentRequest onLogout={onLogout} onCreated={onChanged}/>
 </AdminPage>;
}
