import {useEffect,useState} from 'react';

export default function useAdminData(loader,dependencies,onLogout) {
 const [data,setData]=useState(null),[loading,setLoading]=useState(true),[error,setError]=useState(''),[revision,setRevision]=useState(0);
 useEffect(()=>{
  const controller=new AbortController();setLoading(true);setError('');
  loader(controller.signal).then(result=>{if(!controller.signal.aborted)setData(result);})
   .catch(cause=>{if(!controller.signal.aborted){if(cause.status===401)onLogout();else setError(cause.message);}})
   .finally(()=>{if(!controller.signal.aborted)setLoading(false);});
  return()=>controller.abort();
 },[...dependencies,revision]);
 return {data,setData,loading,error,retry:()=>setRevision(value=>value+1)};
}
