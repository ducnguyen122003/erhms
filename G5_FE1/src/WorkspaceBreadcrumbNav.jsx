import {useEffect,useRef} from 'react';
import {ChevronRight} from 'lucide-react';
import './workspace-breadcrumbs.css';

export default function WorkspaceBreadcrumbs({trail,home,onNavigate}){
 const navRef=useRef(null);
 useEffect(()=>{
  const nav=navRef.current;if(!nav)return;
  const revealCurrent=()=>{
   const current=nav.querySelector('[aria-current=page]');
   if(current)current.style.maxWidth=Math.max(0,Math.min(360,nav.clientWidth-8))+'px';
   nav.scrollLeft=nav.scrollWidth;
  };
  revealCurrent();
  if(typeof ResizeObserver!=='undefined'){const observer=new ResizeObserver(revealCurrent);observer.observe(nav);return()=>observer.disconnect();}
  window.addEventListener('resize',revealCurrent);return()=>window.removeEventListener('resize',revealCurrent);
 },[trail]);
 function follow(event,href,trailIndex){
  if(event.button!==0||event.metaKey||event.ctrlKey||event.shiftKey||event.altKey)return;
  event.preventDefault();onNavigate(href,{trailIndex});
 }
 return <nav ref={navRef} className="erhms-breadcrumb" aria-label="Đường dẫn điều hướng"><ol>
  <li className="erhms-breadcrumb-home">{home?<a href={home} onClick={event=>follow(event,home,null)}>Không gian làm việc</a>:<span>Không gian làm việc</span>}</li>
  {trail.map((item,index)=>{
   const current=index===trail.length-1;
   return <li key={item.href}><ChevronRight size={12} aria-hidden="true"/>{current?<span aria-current="page" title={item.label}>{item.label}</span>:<a href={item.href} title={item.label} onClick={event=>follow(event,item.href,index)}>{item.label}</a>}</li>;
  })}
 </ol></nav>;
}
