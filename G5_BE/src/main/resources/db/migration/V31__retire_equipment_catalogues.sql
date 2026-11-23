DELETE FROM role_function
WHERE function_code IN (
    'ListEquipmentGroup', 'AddEquipmentGroup', 'EditEquipmentGroup',
    'ListStorageLocation', 'AddStorageLocation', 'EditStorageLocation'
);

UPDATE permission_revision SET version=version+1 WHERE id=1;
