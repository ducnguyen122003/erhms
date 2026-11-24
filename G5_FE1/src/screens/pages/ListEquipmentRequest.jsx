import {useState} from 'react';
import BusinessScreen from '../BusinessScreen';
import InitialEquipmentRequest from '../InitialEquipmentRequest';
import {canScreen} from '../access';

export default function ListEquipmentRequest(props) {
  const [listRevision, setListRevision] = useState(0);
  const {user, functions, onLogout, onChanged} = props;
  const canCreate = canScreen('ListEquipmentRequest', user, functions)
    && canScreen('AddEquipmentRequest', user, functions);

  function onCreated() {
    setListRevision(current => current + 1);
    onChanged?.();
  }

  return <BusinessScreen {...props} code="ListEquipmentRequest" listRevision={listRevision}
    listIntro={canCreate && <InitialEquipmentRequest onLogout={onLogout} onCreated={onCreated}/>}/>;
}
