import BusinessScreen from '../BusinessScreen';
import EquipmentRequestNavigation from '../EquipmentRequestNavigation';

export default function ListEquipmentRequest(props) {
  const {user, functions, onNavigate} = props;
  return <BusinessScreen {...props} code="ListEquipmentRequest" listNavigation={
    <EquipmentRequestNavigation activeCode="ListEquipmentRequest" user={user} functions={functions} onNavigate={onNavigate}/>
  }/>;
}
