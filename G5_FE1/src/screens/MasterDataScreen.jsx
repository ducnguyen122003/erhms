import {useEffect,useRef,useState} from 'react';
import {Pencil,Plus,Search,Trash2} from 'lucide-react';
import {api} from '../api';
import {dashboardRoles} from '../roles';
import {AdminDialog,AdminLoading,AdminMessage,AdminPage,AdminPagination} from '../admin/AdminUI';
import useAdminData from '../admin/useAdminData';
import {canScreen,screenPath,screens} from './access';
import ScreenForm from './ScreenForm';
import './screens.css';
import './master-data.css';

const codeLabels={EquipmentGroup:'Mã nhóm thiết bị',StorageLocation:'Mã vị trí',Profession:'Mã ngành nghề',Classroom:'Mã phòng học'};

export default function MasterDataScreen({code,accountId:id,user,functions=[],onNavigate,onLogout,onChanged,initialNotice=''}){
 const spec=screens[code],suffix=spec.masterSuffix,isList=spec.kind==='master_list',codeLabel=codeLabels[suffix];
 const [filter,setFilter]=useState({q:'',status:'',page:0}),[search,setSearch]=useState(''),[busy,setBusy]=useState(false),[error,setError]=useState(''),[errors,setErrors]=useState({}),[notice,setNotice]=useState(initialNotice),[deleting,setDeleting]=useState(null);
 const pending=useRef(false),controllerRef=useRef(null),mounted=useRef(false);
 const url='/api/master-data/screens/'+code+(id?'/'+id:'');
 const remote=useAdminData(signal=>api(url+'?'+new URLSearchParams(filter),{signal}),[url,filter],onLogout);
 useEffect(()=>{mounted.current=true;return()=>{mounted.current=false;controllerRef.current?.abort();};},[]);
 useEffect(()=>{const timer=setTimeout(()=>setFilter(current=>current.q===search?current:{...current,q:search,page:0}),300);return()=>clearTimeout(timer);},[search]);
 const can=target=>canScreen(target,user,functions);
 async function save(values,clearDirty){
  if(pending.current)return false;pending.current=true;setBusy(true);setError('');setErrors({});const controller=new AbortController();controllerRef.current=controller;
  try{const result=await api(url,{method:id?'PUT':'POST',body:JSON.stringify(values),signal:controller.signal});if(!mounted.current)return false;clearDirty();onChanged?.();onNavigate(screenPath(result.screen),{notice:result.notice});return true;}
  catch(cause){if(!controller.signal.aborted){if(cause.status===401)onLogout();else{setError(cause.message);setErrors(cause.fields||{});}}return false;}
  finally{pending.current=false;if(mounted.current)setBusy(false);}
 }
 async function remove(){
  if(pending.current)return;pending.current=true;setBusy(true);setError('');const controller=new AbortController();controllerRef.current=controller;
  try{const result=await api(url+'/'+deleting.id,{method:'DELETE',body:JSON.stringify({version:deleting.version}),signal:controller.signal});if(!mounted.current)return;setNotice(result.notice);setDeleting(null);remote.retry();onChanged?.();}
  catch(cause){if(!controller.signal.aborted){if(cause.status===401)onLogout();else setError(cause.message);}}
  finally{pending.current=false;if(mounted.current)setBusy(false);}
 }
 const fields=[{key:'code',label:codeLabel,type:'text',required:true,maxLength:40},{key:'name',label:'Tên '+screens['List'+suffix].title.toLowerCase(),type:'text',required:true,maxLength:spec.nameLength},{key:'status',label:'Trạng thái',type:'select',source:[{id:'ACTIVE',name:'Đang sử dụng'},{id:'INACTIVE',name:'Ngừng sử dụng'}],required:true},{key:'description',label:'Mô tả',type:'textarea',maxLength:500}];
 const page=<AdminPage title={spec.title} description={spec.description} code={code} eyebrow={dashboardRoles[user.role]?.category||'DANH MỤC DÙNG CHUNG'}>
  {remote.loading||remote.error?<AdminLoading error={remote.error} onRetry={remote.retry}/>:isList?<><AdminMessage error={!deleting?error:''} notice={notice}/><section className="admin-card admin-list-card master-catalogue"><header className="admin-card-heading"><div><h2>{spec.title}</h2><p className="admin-muted">Tìm kiếm, cập nhật và quản lý danh mục dùng chung.</p></div>{can('Add'+suffix)&&<button type="button" onClick={()=>onNavigate(screenPath('Add'+suffix))}><Plus size={15}/>Tạo mới</button>}</header>
  <div className="screen-toolbar"><label className="screen-search"><Search size={15}/><input aria-label="Tìm theo mã hoặc tên" placeholder="Tìm theo mã hoặc tên…" value={search} onChange={e=>setSearch(e.target.value)} maxLength={200}/></label><select aria-label="Lọc trạng thái" value={filter.status} onChange={e=>setFilter(current=>({...current,status:e.target.value,page:0}))}><option value="">Tất cả trạng thái</option><option value="ACTIVE">Đang sử dụng</option><option value="INACTIVE">Ngừng sử dụng</option></select></div>
  <div className="admin-table-scroll"><table className="admin-table"><thead><tr><th>{codeLabel}</th><th>Tên danh mục</th><th>Mô tả</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>{remote.data.items.map(row=><tr key={row.id}><td>{row.code}</td><td>{row.name}</td><td>{row.description||'—'}</td><td><span className={'admin-badge'+(row.status==='INACTIVE'?' is-warning':'')}>{row.status==='ACTIVE'?'Đang sử dụng':'Ngừng sử dụng'}</span></td><td><div className="screen-actions">{can('Edit'+suffix)&&<button type="button" className="admin-text-button" aria-label={'Sửa '+row.name} onClick={()=>onNavigate(screenPath('Edit'+suffix,row.id))}><Pencil size={14}/>Sửa</button>}<button type="button" className="admin-text-button" aria-label={'Xóa '+row.name} onClick={()=>{setError('');setDeleting(row);}}><Trash2 size={14}/>Xóa</button></div></td></tr>)}{!remote.data.items.length&&<tr><td colSpan={5}>Chưa có dữ liệu phù hợp.</td></tr>}</tbody></table></div>
  <AdminPagination page={filter.page} total={remote.data.total} totalPages={remote.data.totalPages} size={25} onPage={page=>setFilter(current=>({...current,page}))}/></section></>:<ScreenForm code={code} fields={fields} record={remote.data.record||{status:'ACTIVE'}} options={{}} user={user} busy={busy} error={error} errors={errors} onSave={save} onBack={()=>onNavigate(screenPath('List'+suffix))} saveLabel={id?'Lưu thay đổi':'Tạo danh mục'}/>}
 </AdminPage>;
 return <>{page}{deleting&&<AdminDialog title="Xóa danh mục" busy={busy} onClose={()=>setDeleting(null)}><div className="admin-dialog-body"><AdminMessage error={error}/><p>Xóa “{deleting.name}” ({deleting.code})?</p><p className="admin-muted">Danh mục đang được sử dụng trong hồ sơ sẽ được giữ lại. Bạn có thể chuyển danh mục sang ngừng sử dụng.</p><div className="admin-dialog-actions"><button type="button" className="admin-secondary" disabled={busy} data-initial-focus onClick={()=>setDeleting(null)}>Hủy</button><button type="button" disabled={busy} onClick={remove}>{busy?'Đang xóa…':'Xóa danh mục'}</button></div></div></AdminDialog>}</>;
}
