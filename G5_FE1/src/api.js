let csrf;
export async function refreshCsrf(){
 const response=await fetch('/api/auth/csrf',{credentials:'same-origin'});
 if(!response.ok)throw new Error('Không kết nối được máy chủ. Kiểm tra backend.');
 csrf=await response.json();
}
export async function api(path,options={}){
 const method=options.method || 'GET';
 if(method!=='GET' && !csrf)await refreshCsrf();
 const headers={...options.headers};
 if(method!=='GET')headers[csrf.headerName]=csrf.token;
 if(options.body && !(options.body instanceof URLSearchParams))headers['Content-Type']='application/json';
 const response=await fetch(path,{credentials:'same-origin',...options,headers});
 if(response.status===204)return null;
 const data=await response.json().catch(()=>({message:'Phản hồi máy chủ không hợp lệ'}));
 if(!response.ok){const error=new Error(data.message || 'Không thể thực hiện thao tác');error.status=response.status;error.fields=data.fields;throw error;}
 return data;
}
export async function login(email,password){await refreshCsrf();await api('/api/auth/login',{method:'POST',body:new URLSearchParams({username:email,password})});await refreshCsrf();return api('/api/auth/me');}
export async function logout(){await api('/api/auth/logout',{method:'POST'});csrf=null;}
