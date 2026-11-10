"""Import design metadata, not the sample operational data, into the application.

Run from the repository root after updating docs/design-access-matrix.json.
The XLSX permissions were read using Python's zipfile/ElementTree (no Excel dependency).
"""
import json
import copy
import re
import argparse
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path

root = Path(__file__).resolve().parent.parent
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--xlsx',type=Path,help='Import the supplied workbook before generating screen metadata')
args=parser.parse_args()
if args.xlsx:
    ns={'m':'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
    role_names=['ADMIN','LECTURER','STUDENT','ACCOUNTANT','IT_SUPPORT','MANAGER','HR']
    imported=[]
    with zipfile.ZipFile(args.xlsx) as workbook:
        strings=[''.join(item.itertext()) for item in ET.fromstring(workbook.read('xl/sharedStrings.xml')).findall('m:si',ns)]
        for row in ET.fromstring(workbook.read('xl/worksheets/sheet1.xml')).findall('m:sheetData/m:row',ns):
            cells={}
            for cell in row.findall('m:c',ns):
                value=cell.find('m:v',ns)
                if value is not None: cells[''.join(filter(str.isalpha,cell.attrib['r']))]=strings[int(value.text)] if cell.attrib.get('t')=='s' else value.text
            if not cells.get('A','').isdigit() or not cells.get('C'):continue
            imported.append(dict(code=cells['C'],fe=cells['B'],title=cells['D'],
                roles=[r for r,c in zip(role_names,'EFGHIJK') if cells.get(c)=='✓'],
                proposedRoles=[r for r,c in zip(role_names,'EFGHIJK') if cells.get(c)=='ĐX'],
                public=cells.get('L')=='✓',scope=cells.get('M',''),actions=cells.get('N',''),variant=cells.get('O','')))
    if len(imported)!=108:raise ValueError('Expected the supplied matrix with exactly 108 screen rows')
    (root/'docs/design-access-matrix.json').write_text(json.dumps(imported,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
design = root / 'ERHMS_Full_Screens_and_Screen_Flow_SVG/ERHMS_Full_UI_SVG'
manifest = json.loads((design / '04_Guides/Screen_Manifest.json').read_text(encoding='utf8'))
matrix = {r['code']: r for r in json.loads((root / 'docs/design-access-matrix.json').read_text(encoding='utf8'))}
aliases = {'ListUser':'USER_ACCOUNTS','EditUser':'USER_ACCOUNTS','UserProfile_AdminView':'USER_ACCOUNTS',
           'RolePermission':'ROLE_PERMISSIONS','SystemConfiguration':'SYSTEM_CONFIG','SystemAuditLog':'SYSTEM_AUDIT_LOG',
           'UserProfile':'PROFILE','ChangePassword':'PROFILE'}
titles = {'StudentProfileDetail':'Hồ sơ học viên','CourseDetail':'Chi tiết khóa học','PublicCourseDetail':'Chi tiết khóa học','AssetDetail':'Chi tiết thiết bị',
          'ClassDetail':'Chi tiết lớp học','ClassDetail_Lecturer':'Lớp được phân công'}
screens = []
for source in manifest['screens']:
    key = source['key']
    item = dict(matrix[key])
    item.update(kind=source.get('kind','dashboard'),title=titles.get(key,item['title']),
                functionCode=aliases.get(key,'DASHBOARD' if key.startswith('Dashboard_') else key))
    description=source.get('desc','')
    if re.search(r'20\d{2}|Nguyễn|Trần|Hoàng|ĐCN-|TĐH-|HĐ-|HV-',description):
        description='Theo dõi và xử lý dữ liệu mới nhất trong phạm vi được phân công.'
    item['description']=description
    for prop in ['headers','tabs','notes','action','actions','target','edit','back','save','filter']:
        if prop in source: item[prop] = source[prop]
    item['sections'] = [{'title':s['title'],'fields':[{'label':f['label'],'type':f['type']} for f in s['fields']]}
                        for s in source.get('sections',[])]
    if key in ('AddAsset','EditAsset'):
        for section in item['sections']:
            section['fields']=[{**field,'label':field['label'].replace('Mã tài sản','Mã thiết bị')}
                               for field in section['fields'] if field['label']!='Số serial']
    screens.append(item)
extension_screens = json.loads((root/'docs/master-screen-catalog.json').read_text(encoding='utf8'))
overrides=json.loads((root/'docs/screen-access-overrides.json').read_text(encoding='utf8'))
runtime_screens=copy.deepcopy(screens)
for screen in runtime_screens:
    screen.update(overrides.get(screen['code'],{}))
for location in ['G5_FE1/src/screens/catalog.json','G5_BE/src/main/resources/workspace/screen-catalog.json']:
    (root/location).write_text(json.dumps(runtime_screens+extension_screens,ensure_ascii=False,indent=2)+'\n',encoding='utf8')

def quote(value): return "'"+str(value).replace("'","''")+"'"
roles={'ADMIN':1,'LECTURER':2,'STUDENT':3,'ACCOUNTANT':4,'IT_SUPPORT':5,'MANAGER':6,'HR':7}
lines=['-- Screen permissions imported from the supplied XLSX. Proposed rights are deliberately not granted.']
for index,s in enumerate(screens):
    if s['public'] or s['functionCode']!=s['code']: continue
    code=quote(s['code'])
    lines.append(f"INSERT INTO m_function(function_code,screen_code,name,path,sort_order) VALUES ({code},{code},{quote(s['title'])},{quote('/screens/'+s['code'])},{100+index});")
    for role in s['roles']:
        lines.append(f"INSERT INTO role_function(role_id,function_code) VALUES ({roles[role]},{code});")
lines.append('UPDATE permission_revision SET version=version+1 WHERE id=1;')
(root/'G5_BE/src/main/resources/db/migration/V10__screen_access_matrix.sql').write_text('\n'.join(lines)+'\n',encoding='utf8')
print(f'Imported {len(screens)} screens; metadata contains no demo students, amounts or dates.')
