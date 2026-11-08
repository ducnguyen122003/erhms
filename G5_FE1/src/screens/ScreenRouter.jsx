import {Suspense,lazy,useEffect} from 'react';
import {AdminLoading} from '../admin/AdminUI';
import {screenPath} from './access';

const modules=import.meta.glob('./pages/*.jsx');
const components=Object.fromEntries(Object.entries(modules).map(([path,loader])=>[path.split('/').at(-1).replace('.jsx',''),lazy(loader)]));
export default function ScreenRouter({screen,id,...props}){
 const Component=components[screen.code];
 useEffect(()=>{if(!Component)props.onNavigate(screenPath(screen.code,id),{replace:true});},[screen.code,id]);
 return Component?<Suspense fallback={<AdminLoading/>}><Component {...props} accountId={id}/></Suspense>:<AdminLoading/>;
}
