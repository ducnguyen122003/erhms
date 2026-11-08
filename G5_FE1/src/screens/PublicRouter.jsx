import {Suspense,lazy} from 'react';
import {screens} from './access';
const modules=import.meta.glob('./public/*.jsx');
const components=Object.fromEntries(Object.entries(modules).map(([path,loader])=>[path.split('/').at(-1).replace('.jsx',''),lazy(loader)]));
export default function PublicRouter({path}){
 const [, ,code='HomePage',id]=path.split('/'),Component=components[code];
 function navigate(target){window.history.pushState({},'',target);window.dispatchEvent(new PopStateEvent('popstate'));window.scrollTo({top:0,behavior:'instant'});}
 if(!Component||!screens[code]?.public)return <main className="loading"><h1>Không tìm thấy trang</h1><a href="/public/HomePage">Về trang chủ</a></main>;
 return <Suspense fallback={<div className="loading">Đang tải trang…</div>}><Component key={path} id={id} onNavigate={navigate}/></Suspense>;
}
